package org.vechain.indexer.contracts

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.contracts.specifications.Contracts
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.ContractUtils.isContractType
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.EventUtils.groupByContractAddress
import org.vechain.indexer.utils.ParamUtils.getAsString

/** Turns `$Master` events into a contract's first row and its later master changes. */
@Profile("contracts", "contract")
@Service
open class ContractService(
    private val repository: ContractWriteRepository,
    private val thorClient: ThorClient,
) {
    /** The new row of each contract touched in each block, in ascending block order. */
    open suspend fun processBlock(events: List<IndexedEvent>): List<Contract> {
        assertEventTypes(events, "\$Master")

        val current =
            repository
                .findCurrentByAddresses(groupByContractAddress(events).keys)
                .associateBy { it.address }
                .toMutableMap()
        val rows = mutableListOf<Contract>()

        groupByBlock(events).forEach { (block, blockEvents) ->
            groupByContractAddress(blockEvents).forEach { (address, contractEvents) ->
                val existing = current[address]
                val updated =
                    if (existing == null) createNewRecord(block, address, contractEvents)
                    else updateExistingRecord(block, contractEvents, existing)
                if (updated != null) {
                    current[address] = updated
                    rows += updated
                }
            }
        }
        return rows
    }

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(contracts: List<Contract>) = repository.save(contracts)

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
        val newMaster =
            events.asReversed().firstNotNullOfOrNull { it.params.getAsString("newMaster") }
                ?: error("No new master in \$Master event")

        return existing.copy(
            blockId = blockDetails.blockId,
            blockNumber = blockDetails.blockNumber,
            blockTimestamp = blockDetails.blockTimestamp,
            master = newMaster,
        )
    }
}
