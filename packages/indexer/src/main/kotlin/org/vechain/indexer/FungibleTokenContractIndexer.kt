package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedFungibleTokenContracts
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.repository.FungibleTokenContractsRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.service.FungibleContractsService
import org.vechain.indexer.utils.BlockUtils
import org.vechain.thor.model.Block
import java.util.*

@Profile("fungible-token-contracts")
@Component
open class FungibleTokenContractIndexer(
    private val archiveService: ArchiveService,
    private val fungibleContractsService: FungibleContractsService,
    thorClient: ThorClient,
    fungibleTokenContractsRepository: FungibleTokenContractsRepository,
    @Value("\${indexer.startBlock.fungibleTokens}") private val startBlock: Long,
    @Value("\${indexer.syncLoggerInterval.fungibleTokens}") private val syncLoggerInterval: Long,
) :
    VeWorldIndexer(
        repository = fungibleTokenContractsRepository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLoggerInterval = syncLoggerInterval
    ) {

    override fun processBlock(block: Block) {
        // get the fungible transfers
        val fungibleTransfers = BlockUtils.extractFungibleTransfers(block)

        // account -> set of contracts
        val accountContractMap = getContractsForAccount(fungibleTransfers)

        // process each account's contracts
        val documents =
            accountContractMap
                .map { (account, contracts) -> processAccountsContracts(account, contracts, block) }
                .mapNotNull { it }

        fungibleContractsService.saveAll(
            existing = documents.map { it.first },
            archived = documents.mapNotNull { it.second }
        )
    }

    private fun getContractsForAccount(
        transferEvents: List<IndexedTransferEvent>
    ): MutableMap<String, SortedSet<String>> {
        // account -> list of contracts
        val accountContractMap: MutableMap<String, SortedSet<String>> = mutableMapOf()

        fun addContract(contract: String, account: String) {
            val contracts = accountContractMap[account] ?: sortedSetOf()
            contracts.add(contract)
            accountContractMap[account] = contracts
        }

        // populate the map
        transferEvents.forEach {
            val tokenAddress = it.tokenAddress ?: return@forEach

            addContract(tokenAddress, it.to)
            addContract(tokenAddress, it.from)
        }

        return accountContractMap
    }

    /**
     * Process the contracts for an account
     *
     * @returns null if the documents are not updated
     * @returns Pair<newDocument, null> if is the accounts first entry
     * @returns Pair<newDocument, oldDocument> if the documents are updated
     */
    private fun processAccountsContracts(
        accAddress: String,
        contracts: SortedSet<String>,
        block: Block
    ): Pair<IndexedFungibleTokenContracts, IndexedFungibleTokenContracts?>? {

        val previousRecord =
            fungibleContractsService.getExisting(accAddress)
            // Return a new document if the account has no previous record
                ?: return Pair(
                    IndexedFungibleTokenContracts(
                        tokenOwner = accAddress,
                        tokenAddresses = contracts,
                        blockNumber = block.number,
                        blockId = block.id,
                        blockTimestamp = block.timestamp,
                        version = 1
                    ),
                    null
                )

        // return null if no contracts are added - no update required
        if (previousRecord.tokenAddresses.containsAll(contracts)) {
            return null
        }

        // Update the contracts
        val updatedContracts = previousRecord.tokenAddresses.toSortedSet()
        updatedContracts.addAll(contracts)

        val latestDocument =
            IndexedFungibleTokenContracts(
                tokenOwner = accAddress,
                tokenAddresses = updatedContracts,
                blockNumber = block.number,
                blockId = block.id,
                blockTimestamp = block.timestamp,
                version = previousRecord.version + 1
            )

        return Pair(latestDocument, previousRecord)
    }

    override fun rollback(blockNumber: Long) {
        archiveService.rollback(blockNumber, IndexedFungibleTokenContracts::class.java)
    }
}
