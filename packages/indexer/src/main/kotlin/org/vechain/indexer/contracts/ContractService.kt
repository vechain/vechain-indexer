package org.vechain.indexer.contracts

import kotlin.collections.component1
import kotlin.collections.component2
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.contracts.repository.ContractRepository
import org.vechain.indexer.contracts.specifications.Contracts
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.ContractUtils.isContractType
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.EventUtils.groupByContractAddress
import org.vechain.indexer.utils.IdUtils.generateId
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("contracts", "contract")
@Service
open class ContractService(
    private val repository: ContractRepository,
    private val archiveService: ArchiveService<Contract, ContractArchive>,
    private val contractPruner: TargetedPruner<Contract, ContractArchive>,
    private val thorClient: ThorClient,
) {
    open suspend fun processBlock(
        events: List<IndexedEvent>
    ): Pair<List<Contract>, List<Contract>> {
        assertEventTypes(events, "\$Master")

        val updatedResult = mutableMapOf<String, Contract>()
        val archiveResult = mutableListOf<Contract>()
        groupByBlock(events).forEach { (blockDetails, blockEvents) ->
            groupByContractAddress(blockEvents).forEach { (contractAddress, contractEvents) ->
                val recordId = generateId(blockDetails.blockId, contractAddress)
                val existing = resolveExisting(recordId, updatedResult)
                val updated =
                    createOrUpdateExisting(blockDetails, contractAddress, contractEvents, existing)
                updated?.let { u ->
                    existing?.let { e -> archiveResult.add(e) }
                    updatedResult[recordId] = u
                }
            }
        }

        return Pair(updatedResult.values.toList(), archiveResult)
    }

    @Transactional
    open fun save(updated: List<Contract>, existing: List<Contract>) {
        saveVersionedDocuments(
            updated = updated,
            existing = existing,
            repository = repository,
            archiveService = archiveService,
            pruner = contractPruner,
        )
    }

    protected suspend fun createOrUpdateExisting(
        blockDetails: BlockDetails,
        contractAddress: String,
        events: List<IndexedEvent>,
        existing: Contract?,
    ): Contract? {
        return if (existing == null) {
            createNewRecord(blockDetails, contractAddress, events)
        } else {
            updateExistingRecord(blockDetails, events, existing)
        }
    }

    protected suspend fun createNewRecord(
        blockDetails: BlockDetails,
        contractAddress: String,
        events: List<IndexedEvent>,
    ): Contract? {

        // Use the last $Master event in the block to derive the latest master.
        val master =
            events.asReversed().firstNotNullOfOrNull { it.params.getAsString("newMaster") }
                ?: return null

        // Get the contract code. If none exists this isn't a contract
        val accountCode =
            thorClient.getAccountCode(contractAddress, BlockRevision.Id(blockDetails.blockId))
        if (accountCode.code == "0x") {
            return null
        }

        return Contract(
            address = contractAddress,
            blockId = blockDetails.blockId,
            blockNumber = blockDetails.blockNumber,
            blockTimestamp = blockDetails.blockTimestamp,
            version = 0,
            createdOn = blockDetails.blockTimestamp,
            deploymentTxId = events.first().txId,
            deploymentClauseIndex = events.first().clauseIndex,
            master = master,
            isErc20 = isContractType(Contracts.ERC20, accountCode.code),
            isErc721 = isContractType(Contracts.ERC721, accountCode.code),
            isErc1155 = isContractType(Contracts.ERC1155, accountCode.code),
        )
    }

    protected fun updateExistingRecord(
        blockDetails: BlockDetails,
        events: List<IndexedEvent>,
        existing: Contract,
    ): Contract {
        // We only care about the last Master event in the block when updating
        val newMaster =
            events.first().params.getAsString("newMaster") ?: error("No newMaster in event")

        return existing.copy(
            blockId = blockDetails.blockId,
            blockNumber = blockDetails.blockNumber,
            blockTimestamp = blockDetails.blockTimestamp,
            version = existing.version + 1,
            master = newMaster,
        )
    }

    protected fun resolveExisting(recordId: String, cache: Map<String, Contract>): Contract? =
        cache[recordId] ?: repository.findByIdOrNull(recordId)
}
