package org.vechain.indexer.contracts

import kotlin.collections.component1
import kotlin.collections.component2
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.contracts.repository.ContractRepository
import org.vechain.indexer.contracts.specifications.Contracts
import org.vechain.indexer.event.model.generic.IndexedEvent
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
    private val inlineVersioningProperties: InlineVersioningProperties,
    private val mongoTemplate: MongoTemplate,
    private val thorClient: ThorClient,
) {
    open suspend fun processBlock(
        events: List<IndexedEvent>
    ): Pair<List<Contract>, List<Contract>> {
        assertEventTypes(events, "\$Master")

        // Pre-collect all record IDs and batch-load from DB
        val allRecordIds = mutableSetOf<String>()
        groupByBlock(events).forEach { (blockDetails, blockEvents) ->
            groupByContractAddress(blockEvents).forEach { (contractAddress, _) ->
                allRecordIds.add(generateId(blockDetails.blockId, contractAddress))
            }
        }
        val preloaded =
            if (allRecordIds.isNotEmpty()) {
                repository.findAllById(allRecordIds).associateBy { it.getDocumentId() }
            } else {
                emptyMap()
            }

        val accumulator =
            VersionedDocumentAccumulator<Contract>(
                findById = { id -> preloaded[id] ?: repository.findByIdOrNull(id) },
                initialVersion = 1,
            )
        groupByBlock(events).forEach { (blockDetails, blockEvents) ->
            accumulator.startBlock()
            groupByContractAddress(blockEvents).forEach { (contractAddress, contractEvents) ->
                val recordId = generateId(blockDetails.blockId, contractAddress)
                val (existing, nextVersion) = accumulator.resolve(recordId)
                val updated =
                    createOrUpdateExisting(
                        blockDetails,
                        contractAddress,
                        contractEvents,
                        existing,
                        nextVersion,
                    )
                if (updated != null) {
                    accumulator.put(recordId, existing, updated)
                }
            }
        }

        return accumulator.results()
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<Contract>, existing: List<Contract>) {
        saveVersionedDocuments(
            updated = updated,
            existing = existing,
            mongoTemplate = mongoTemplate,
            blockWindow = inlineVersioningProperties.blockWindow,
            maxVersions = inlineVersioningProperties.maxVersions,
        )
    }

    protected suspend fun createOrUpdateExisting(
        blockDetails: BlockDetails,
        contractAddress: String,
        events: List<IndexedEvent>,
        existing: Contract?,
        version: Int,
    ): Contract? {
        return if (existing == null) {
            createNewRecord(blockDetails, contractAddress, events, version)
        } else {
            updateExistingRecord(blockDetails, events, existing, version)
        }
    }

    protected suspend fun createNewRecord(
        blockDetails: BlockDetails,
        contractAddress: String,
        events: List<IndexedEvent>,
        version: Int,
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
            version = version,
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
        version: Int,
    ): Contract {
        val newMaster =
            events.asReversed().firstNotNullOfOrNull { it.params.getAsString("newMaster") }
                ?: error("No new master in \$Master event")

        return existing.copy(
            blockId = blockDetails.blockId,
            blockNumber = blockDetails.blockNumber,
            blockTimestamp = blockDetails.blockTimestamp,
            version = version,
            master = newMaster,
        )
    }
}
