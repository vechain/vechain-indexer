package org.vechain.indexer.stargate.nftOwnerBalance

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.nftHolders.NftOwnerBalance
import org.vechain.indexer.stargate.nftHolders.NftOwnerBalanceRepository
import org.vechain.indexer.stargate.requireLevel
import org.vechain.indexer.stargate.requireOwner
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("stargate", "nft-owner-balance")
@Service
open class NftOwnerBalanceService(private val repository: NftOwnerBalanceRepository) {
    /**
     * Process raw NFT stake/unstake events across multiple blocks and produce one [NftOwnerBalance]
     * document per affected owner per block.
     *
     * @param events The decoded on-chain events grouped across arbitrary blocks.
     * @return A list of [NftOwnerBalance] documents in ascending block order.
     * @throws IllegalStateException if any event's block number is at or before the last stored
     *   block.
     */
    open fun processEvents(events: List<IndexedEvent>): List<NftOwnerBalance> {
        if (events.isEmpty()) return emptyList()

        val grouped = events.groupBy { it.blockNumber }.toSortedMap()
        val lowestBlock = grouped.firstKey()

        // Load existing owner balances for all owners before the lowest block in this batch
        val allOwners = events.mapNotNull { it.params.getAsString("owner") }.toSet()
        val existingBalances =
            if (allOwners.isNotEmpty()) {
                repository.findLatestBalancesBeforeBlock(allOwners, lowestBlock)
            } else {
                emptyList()
            }
        val ownerBalances = existingBalances.associateBy { it.owner }.toMutableMap()

        val output = mutableListOf<NftOwnerBalance>()

        for ((blockNum, blockEvents) in grouped) {
            // Track which owners are affected in this block
            val affectedOwners = mutableSetOf<String>()

            blockEvents.forEach { evt ->
                val level = evt.requireLevel()
                val owner = evt.requireOwner()
                affectedOwners += owner

                val currentBalance = ownerBalances[owner]
                val currentTotal = currentBalance?.total ?: 0L
                val currentByLevel = currentBalance?.byLevel?.toMutableMap() ?: mutableMapOf()

                when (evt.eventType) {
                    "STARGATE_STAKE" -> {
                        currentByLevel[level] = (currentByLevel[level] ?: 0L) + 1
                        ownerBalances[owner] =
                            NftOwnerBalance(
                                owner = owner,
                                total = currentTotal + 1,
                                byLevel = currentByLevel.toMap(),
                                blockNumber = blockNum,
                                blockId = evt.blockId,
                                blockTimestamp = evt.blockTimestamp,
                            )
                    }

                    "STARGATE_UNSTAKE" -> {
                        currentByLevel[level] = (currentByLevel[level] ?: 0L) - 1
                        ownerBalances[owner] =
                            NftOwnerBalance(
                                owner = owner,
                                total = currentTotal - 1,
                                byLevel = currentByLevel.toMap(),
                                blockNumber = blockNum,
                                blockId = evt.blockId,
                                blockTimestamp = evt.blockTimestamp,
                            )
                    }

                    else -> throw IllegalArgumentException("Unknown eventType: ${evt.eventType}")
                }
            }

            // Emit one doc per affected owner for this block
            affectedOwners.forEach { owner -> ownerBalances[owner]?.let { output += it } }
        }

        return output
    }

    open fun saveRecords(records: List<NftOwnerBalance>) {
        repository.saveAll(records)
    }
}
