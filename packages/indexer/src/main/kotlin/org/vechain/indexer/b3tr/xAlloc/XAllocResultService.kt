package org.vechain.indexer.b3tr.xAlloc

import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.b3tr.xAlloc.XAllocEventUtils.getAmountAsDecimal
import org.vechain.indexer.b3tr.xAlloc.XAllocEventUtils.getAppId
import org.vechain.indexer.b3tr.xAlloc.XAllocEventUtils.getRewardsAllocationAmountAsDecimal
import org.vechain.indexer.b3tr.xAlloc.XAllocEventUtils.getTeamAllocationAmountAsDecimal
import org.vechain.indexer.b3tr.xAlloc.XAllocEventUtils.getTotalAmountAsDecimal
import org.vechain.indexer.b3tr.xAlloc.XAllocEventUtils.getUnallocatedAmountAsDecimal
import org.vechain.indexer.b3tr.xAlloc.XAllocEventUtils.groupByRoundId
import org.vechain.indexer.b3tr.xAlloc.XAllocEventUtils.parseVotes
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.EventUtils.groupByBlock

@Profile("b3tr", "b3tr-x-alloc")
@Service
open class XAllocResultService(
    private val repository: XAllocResultWriteRepository,
    private val thorClient: ThorClient,
    @param:Value("\${business-event.substitutions.X_ALLOC_POOL_CONTRACT}")
    private val xAllocPoolContract: String,
) {

    private val logger = LoggerFactory.getLogger(XAllocResultService::class.java)

    private val cachedIsQuadraticFundingEnabled: ConcurrentHashMap<Int, Boolean> =
        ConcurrentHashMap()
    private val isQuadraticFundingDisabledAbi: AbiElement by lazy {
        val abis =
            AbiLoader.loadFunctions("abis/b3tr", listOf("isQuadraticFundingDisabledForRound"))
        val abi =
            abis.firstOrNull { it.name == "isQuadraticFundingDisabledForRound" }
                ?: throw IllegalArgumentException(
                    "Function 'isQuadraticFundingDisabledForRound' not found in ABI"
                )
        abi
    }

    /** The new row of each (round, app) touched in each block, in ascending block order. */
    open suspend fun processEvents(events: List<IndexedEvent>): List<XAllocResult> {
        if (events.isEmpty()) return emptyList()

        val rounds = mutableSetOf<Int>()
        val apps = mutableSetOf<String>()
        groupByRoundId(events).forEach { (roundId, roundEvents) ->
            rounds += roundId
            roundEvents.forEach { event ->
                when (event.eventType) {
                    "B3TR_XAllocationVote" -> apps += XAllocEventUtils.getAppIds(event)
                    "B3TR_XAllocationRewardsClaimed",
                    "B3TR_DBAFundsDistributed" -> apps += getAppId(event)
                }
            }
        }
        val current =
            repository
                .findCurrent(rounds, apps)
                .associateBy { it.roundId to it.appId }
                .toMutableMap()
        // One row per (block, round, app): a later event in the same block replaces the earlier.
        val rows = linkedMapOf<Triple<Long, Int, String>, XAllocResult>()

        fun record(blockNumber: Long, updated: XAllocResult) {
            current[updated.roundId to updated.appId] = updated
            rows[Triple(blockNumber, updated.roundId, updated.appId)] = updated
        }

        val bestBlockId = thorClient.getBlockUnexpanded(BlockRevision.Keyword.BEST).id

        groupByBlock(events).forEach { (blockDetails, blockEvents) ->
            groupByRoundId(blockEvents).forEach { (roundId, roundEvents) ->
                val isQFEnabled = isQuadraticFundingEnabled(roundId, bestBlockId)
                parseVotes(
                        roundEvents.filter { it.eventType == "B3TR_XAllocationVote" },
                        isQFEnabled,
                    )
                    .forEach { (appId, aggregatedVote) ->
                        record(
                            blockDetails.blockNumber,
                            addOrCreateVoteResult(
                                roundId = roundId,
                                appId = appId,
                                voters = aggregatedVote.voters,
                                votesReceived = aggregatedVote.votesReceived,
                                blockDetails = blockDetails,
                                existing = current[roundId to appId],
                            ),
                        )
                    }
                roundEvents
                    .filter { it.eventType == "B3TR_XAllocationRewardsClaimed" }
                    .forEach { event ->
                        val appId = getAppId(event)
                        record(
                            blockDetails.blockNumber,
                            addOrCreateRewardClaimResult(
                                roundId = roundId,
                                appId = appId,
                                blockDetails = blockDetails,
                                existing = current[roundId to appId],
                                totalAmount = getTotalAmountAsDecimal(event),
                                unallocatedAmount = getUnallocatedAmountAsDecimal(event),
                                teamAllocationAmount = getTeamAllocationAmountAsDecimal(event),
                                rewardsAllocationAmount =
                                    getRewardsAllocationAmountAsDecimal(event),
                            ),
                        )
                    }
                roundEvents
                    .filter { it.eventType == "B3TR_DBAFundsDistributed" }
                    .forEach { event ->
                        val appId = getAppId(event)
                        record(
                            blockDetails.blockNumber,
                            addOrCreateDbaFundResult(
                                roundId = roundId,
                                appId = appId,
                                blockDetails = blockDetails,
                                existing = current[roundId to appId],
                                amount = getAmountAsDecimal(event),
                            ),
                        )
                    }
            }
        }

        return rows.values.toList()
    }

    protected fun addOrCreateVoteResult(
        roundId: Int,
        appId: String,
        blockDetails: BlockDetails,
        existing: XAllocResult?,
        voters: Long,
        votesReceived: BigInteger,
    ): XAllocResult {
        return existing?.copy(
            blockId = blockDetails.blockId,
            blockNumber = blockDetails.blockNumber,
            blockTimestamp = blockDetails.blockTimestamp,
            voters = existing.voters + voters,
            votesReceived = existing.votesReceived + votesReceived,
        )
            ?: XAllocResult(
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                roundId = roundId,
                appId = appId,
                voters = voters,
                votesReceived = votesReceived,
                totalAmount = null,
                unallocatedAmount = null,
                teamAllocationAmount = null,
                rewardsAllocationAmount = null,
            )
    }

    protected fun addOrCreateRewardClaimResult(
        roundId: Int,
        appId: String,
        blockDetails: BlockDetails,
        existing: XAllocResult?,
        totalAmount: BigDecimal,
        unallocatedAmount: BigDecimal,
        teamAllocationAmount: BigDecimal,
        rewardsAllocationAmount: BigDecimal,
    ): XAllocResult {
        return existing?.copy(
            blockId = blockDetails.blockId,
            blockNumber = blockDetails.blockNumber,
            blockTimestamp = blockDetails.blockTimestamp,
            totalAmount = existing.totalAmount?.plus(totalAmount) ?: totalAmount,
            unallocatedAmount =
                existing.unallocatedAmount?.plus(unallocatedAmount) ?: unallocatedAmount,
            teamAllocationAmount =
                existing.teamAllocationAmount?.plus(teamAllocationAmount) ?: teamAllocationAmount,
            rewardsAllocationAmount =
                existing.rewardsAllocationAmount?.plus(rewardsAllocationAmount)
                    ?: rewardsAllocationAmount,
        )
            ?: XAllocResult(
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                roundId = roundId,
                appId = appId,
                voters = 0,
                votesReceived = BigInteger.ZERO,
                totalAmount = totalAmount,
                unallocatedAmount = unallocatedAmount,
                teamAllocationAmount = teamAllocationAmount,
                rewardsAllocationAmount = rewardsAllocationAmount,
            )
    }

    protected fun addOrCreateDbaFundResult(
        roundId: Int,
        appId: String,
        blockDetails: BlockDetails,
        existing: XAllocResult?,
        amount: BigDecimal,
    ): XAllocResult {
        return existing?.copy(
            blockId = blockDetails.blockId,
            blockNumber = blockDetails.blockNumber,
            blockTimestamp = blockDetails.blockTimestamp,
            totalAmount = existing.totalAmount?.plus(amount) ?: amount,
        )
            ?: XAllocResult(
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                roundId = roundId,
                appId = appId,
                voters = 0,
                votesReceived = BigInteger.ZERO,
                totalAmount = amount,
                unallocatedAmount = null,
                teamAllocationAmount = null,
                rewardsAllocationAmount = null,
            )
    }

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(results: List<XAllocResult>) = repository.save(results)

    open suspend fun isQuadraticFundingEnabled(roundId: Int, bestBlockId: String): Boolean =
        cachedIsQuadraticFundingEnabled[roundId]
            ?: run {
                val clause =
                    ContractUtils.createClause(
                        address = xAllocPoolContract,
                        function = isQuadraticFundingDisabledAbi,
                        BigInteger.valueOf(roundId.toLong()),
                    )
                val response =
                    thorClient.inspectClauses(listOf(clause), BlockRevision.Id(bestBlockId))
                val decoded =
                    FunctionReturnDecoder.decode(
                        response[0].data,
                        isQuadraticFundingDisabledAbi.outputs,
                    )
                val isDisabled = decoded[""] as Boolean
                cachedIsQuadraticFundingEnabled[roundId] = !isDisabled
                !isDisabled
            }
}
