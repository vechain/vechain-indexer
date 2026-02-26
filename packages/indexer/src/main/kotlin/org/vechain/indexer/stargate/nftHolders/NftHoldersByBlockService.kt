package org.vechain.indexer.stargate.nftHolders

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.utils.CacheUtils
import org.vechain.indexer.utils.ParamUtils.getAsInt
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.utils.RolloverUtils

@Profile("stargate", "nft-holders-by-block")
@Service
open class NftHoldersByBlockService(
    private val repository: NftHoldersByBlockRepository,
    private val ownerBalanceRepository: NftOwnerBalanceRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {
    private var latestRecordCache: NftHoldersByBlock? = null

    /**
     * @param events The decoded on-chain events grouped across arbitrary blocks.
     * @return A list of `NftHoldersByBlock` documents in ascending block order.
     * @notice Process raw NFT stake/unstake events across multiple blocks.
     * @dev Per-block flow:
     *     1. Track unique holders by maintaining per-owner NFT balances.
     *     2. Only count as new holder when balance goes 0→1, remove when 1→0.
     *     3. Apply rollover logic relative to *the immediate previous block*.
     *     4. If rollover occurred, emit an updated copy of the previous row.
     *     5. Emit the new row for the current block.
     *
     *    State tracking:
     *         - `prev` always refers to the most recently emitted record (or the DB record if this
     *           is the first block).
     *         - Owner balances are persisted separately to track unique holders.
     *
     *   Strict ordering:
     *         - Throws if any event's block number ≤ last stored block number.
     */
    open fun processEvents(events: List<IndexedEvent>): List<NftHoldersByBlock> {
        if (events.isEmpty()) return emptyList()

        val latest = latestRecordCache ?: repository.getLatestRecord()
        val lastBlock = latest?.blockNumber

        // Enforce strict ascending block ordering
        if (lastBlock != null && events.any { it.blockNumber <= lastBlock }) {
            throw IllegalStateException("Events include block ≤ last persisted block $lastBlock")
        }

        // Load existing owner balances for all owners in this batch
        val allOwners = events.mapNotNull { it.params.getAsString("owner") }.toSet()
        val existingBalances = ownerBalanceRepository.findByOwnerIn(allOwners)
        val existingBalancesByOwner = existingBalances.associateBy { it.owner }
        val ownerBalances = existingBalancesByOwner.toMutableMap()

        // Track balances to archive (existing balances that will be updated)
        val balancesToArchive = mutableListOf<NftOwnerBalance>()

        var runningTotal = latest?.total ?: 0L
        val runningByLevel = latest?.byLevel?.toMutableMap() ?: mutableMapOf<TokenLevel, Long>()

        val grouped = events.groupBy { it.blockNumber }.toSortedMap()
        val output = mutableListOf<NftHoldersByBlock>()
        var prev: NftHoldersByBlock? = latest

        for ((blockNum, blockEvents) in grouped) {
            var blockDelta = 0L

            blockEvents.forEach { evt ->
                val level = evt.requireLevel()
                val owner = evt.requireOwner()

                // Get current balance for this owner
                val currentBalance = ownerBalances[owner]
                val currentTotal = currentBalance?.total ?: 0L
                val currentByLevel = currentBalance?.byLevel?.toMutableMap() ?: mutableMapOf()

                val currentVersion = currentBalance?.version ?: 0

                // Archive existing balance if it will be updated
                if (currentBalance != null && !balancesToArchive.any { it.owner == owner }) {
                    balancesToArchive += currentBalance
                }

                when (evt.eventType) {
                    "STARGATE_STAKE" -> {
                        // New unique holder if they had 0 NFTs before
                        if (currentTotal == 0L) {
                            runningTotal += 1
                            blockDelta += 1
                        }
                        // New holder at this level if they had 0 NFTs at this level
                        if ((currentByLevel[level] ?: 0L) == 0L) {
                            runningByLevel[level] = (runningByLevel[level] ?: 0L) + 1
                        }

                        // Update owner balance
                        currentByLevel[level] = (currentByLevel[level] ?: 0L) + 1
                        ownerBalances[owner] =
                            NftOwnerBalance(
                                owner = owner,
                                total = currentTotal + 1,
                                byLevel = currentByLevel.toMap(),
                                blockNumber = evt.blockNumber,
                                blockId = evt.blockId,
                                blockTimestamp = evt.blockTimestamp,
                                version = currentVersion + 1,
                            )
                    }

                    "STARGATE_UNSTAKE" -> {
                        val newTotal = currentTotal - 1
                        val newLevelBalance = (currentByLevel[level] ?: 0L) - 1

                        // No longer a unique holder if they now have 0 NFTs
                        if (newTotal == 0L && currentTotal > 0L) {
                            runningTotal -= 1
                            blockDelta -= 1
                        }
                        // No longer a holder at this level if they now have 0 NFTs at this level
                        if (newLevelBalance == 0L && (currentByLevel[level] ?: 0L) > 0L) {
                            runningByLevel[level] = (runningByLevel[level] ?: 0L) - 1
                        }

                        // Update owner balance
                        currentByLevel[level] = newLevelBalance
                        ownerBalances[owner] =
                            NftOwnerBalance(
                                owner = owner,
                                total = newTotal,
                                byLevel = currentByLevel.toMap(),
                                blockNumber = evt.blockNumber,
                                blockId = evt.blockId,
                                blockTimestamp = evt.blockTimestamp,
                                version = currentVersion + 1,
                            )
                    }

                    else -> throw IllegalArgumentException("Unknown eventType: ${evt.eventType}")
                }
            }

            val rep = blockEvents.first()

            val roll =
                RolloverUtils.calculateRollover(
                    blockTimestamp = rep.blockTimestamp,
                    delta = BigInteger.valueOf(blockDelta), // convert delta → BigInteger
                    ctx =
                        RolloverUtils.Context(
                            prevHourTotal = prev?.hourTotal ?: BigInteger.ZERO,
                            prevDayTotal = prev?.dayTotal ?: BigInteger.ZERO,
                            prevWeekTotal = prev?.weekTotal ?: BigInteger.ZERO,
                            prevMonthTotal = prev?.monthTotal ?: BigInteger.ZERO,
                            prevYearTotal = prev?.yearTotal ?: BigInteger.ZERO,
                            prevHour = prev?.hourOfDay,
                            prevDay = prev?.dayOfMonth,
                            prevWeek = prev?.weekOfYear,
                            prevMonth = prev?.month,
                            prevYear = prev?.year,
                        ),
                )

            if (roll.timeFrames.isNotEmpty() && prev != null) {
                output += prev.copy(timeFrames = roll.timeFrames)
            }

            val doc =
                NftHoldersByBlock(
                    blockId = rep.blockId,
                    blockNumber = blockNum,
                    blockTimestamp = rep.blockTimestamp,
                    total = runningTotal,
                    byLevel = runningByLevel.toMap(),
                    hourOfDay = roll.hour,
                    dayOfMonth = roll.day,
                    weekOfYear = roll.week,
                    month = roll.month,
                    year = roll.year,
                    timeFrames = emptyList(),
                    blockTotal = BigInteger.valueOf(blockDelta),
                    hourTotal = roll.hourTotal,
                    dayTotal = roll.dayTotal,
                    weekTotal = roll.weekTotal,
                    monthTotal = roll.monthTotal,
                    yearTotal = roll.yearTotal,
                )

            output += doc
            prev = doc
        }

        // Store updated owner balances and those to archive
        updatedOwnerBalances = ownerBalances.values.toList()
        ownerBalancesToArchive = balancesToArchive

        return output
    }

    // Temporary storage for updated balances to be saved with records
    private var updatedOwnerBalances: List<NftOwnerBalance> = emptyList()
    private var ownerBalancesToArchive: List<NftOwnerBalance> = emptyList()

    /** @notice Persist multiple per-block NFT holder statistics records. */
    @Transactional(rollbackFor = [Exception::class])
    open fun saveRecords(records: List<NftHoldersByBlock>) {
        repository.saveAll(records)
        saveOwnerBalances()
        if (records.isNotEmpty()) {
            val latest = records.maxBy { it.blockNumber }
            CacheUtils.updateAfterCommit(
                latest,
                { latestRecordCache = it },
                { latestRecordCache = null },
            )
        }
    }

    private fun saveOwnerBalances() {
        if (updatedOwnerBalances.isNotEmpty()) {
            saveVersionedDocuments(
                updatedOwnerBalances,
                ownerBalancesToArchive,
                mongoTemplate,
                inlineVersioningProperties.blockWindow,
                inlineVersioningProperties.maxVersions,
            )
            updatedOwnerBalances = emptyList()
            ownerBalancesToArchive = emptyList()
        }
    }
}

/** Require TokenLevel */
private fun IndexedEvent.requireLevel(): TokenLevel {
    val id =
        this.params.getAsInt("levelId")
            ?: throw IllegalArgumentException("Missing levelId in event params")

    return TokenLevel.fromOrdinal(id) ?: throw IllegalArgumentException("Invalid levelId: $id")
}

/** Require owner address */
private fun IndexedEvent.requireOwner(): String {
    return this.params.getAsString("owner")
        ?: throw IllegalArgumentException("Missing owner in event params")
}
