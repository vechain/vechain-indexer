package org.vechain.indexer.b3tr.xAlloc

import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.IdUtils.generateId
import org.vechain.indexer.b3tr.xAlloc.XAllocEventUtils.getAmountAsDecimal
import org.vechain.indexer.b3tr.xAlloc.XAllocEventUtils.getAppId
import org.vechain.indexer.b3tr.xAlloc.XAllocEventUtils.getRewardsAllocationAmountAsDecimal
import org.vechain.indexer.b3tr.xAlloc.XAllocEventUtils.getTeamAllocationAmountAsDecimal
import org.vechain.indexer.b3tr.xAlloc.XAllocEventUtils.getTotalAmountAsDecimal
import org.vechain.indexer.b3tr.xAlloc.XAllocEventUtils.getUnallocatedAmountAsDecimal
import org.vechain.indexer.b3tr.xAlloc.XAllocEventUtils.groupByRoundId
import org.vechain.indexer.b3tr.xAlloc.XAllocEventUtils.parseVotes
import org.vechain.indexer.b3tr.xAlloc.repository.XAllocResultRepository
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.EventUtils.groupByBlock

@Profile("b3tr", "b3tr-x-alloc")
@Service
open class XAllocResultService(
    private val repository: XAllocResultRepository,
    private val xAllocResultArchiveService: ArchiveService<XAllocResult, XAllocResultArchive>,
    private val xAllocResultPruner: TargetedPruner<XAllocResult, XAllocResultArchive>,
    private val thorService: ThorService,
    @param:Value("\${business-event.substitutions.X_ALLOC_POOL_CONTRACT}")
    private val xAllocPoolContract: String,
) {

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

    open fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<XAllocResult>, List<XAllocResult>> {
        val updatedResult = mutableMapOf<String, XAllocResult>()
        val archiveResult = mutableListOf<XAllocResult>()

        groupByBlock(events).forEach { (blockDetails, blockEvents) ->
            groupByRoundId(blockEvents).forEach { (roundId, roundEvents) ->
                val isQFEnabled = isQuadraticFundingEnabled(roundId)
                // Parse vote events
                parseVotes(
                        roundEvents.filter { it.eventType == "B3TR_XAllocationVote" },
                        isQFEnabled,
                    )
                    .forEach { (appId, aggregatedVote) ->
                        val recordId = generateId("$roundId", appId)
                        val existing = resolveExisting(recordId, updatedResult)
                        val updated =
                            createOrUpdateExisting(
                                roundId = roundId,
                                appId = appId,
                                voters = aggregatedVote.voters,
                                votesReceived = aggregatedVote.votesReceived,
                                blockDetails = blockDetails,
                                existing = existing,
                            )

                        updatedResult[recordId] = updated
                        existing?.let { archiveResult.add(it) }
                    }
                // Parse ClaimReward events
                roundEvents
                    .filter { it.eventType == "B3TR_XAllocationRewardsClaimed" }
                    .forEach { event ->
                        val appId = getAppId(event)
                        val totalAmount = getTotalAmountAsDecimal(event)
                        val unallocatedAmount = getUnallocatedAmountAsDecimal(event)
                        val teamAllocationAmount = getTeamAllocationAmountAsDecimal(event)
                        val rewardsAllocationAmount = getRewardsAllocationAmountAsDecimal(event)
                        val recordId = generateId("$roundId", appId)
                        val existing = resolveExisting(recordId, updatedResult)
                        val updated =
                            createOrUpdateExisting(
                                roundId = roundId,
                                appId = appId,
                                blockDetails = blockDetails,
                                existing = existing,
                                totalAmount = totalAmount,
                                unallocatedAmount = unallocatedAmount,
                                teamAllocationAmount = teamAllocationAmount,
                                rewardsAllocationAmount = rewardsAllocationAmount,
                            )
                        updatedResult[recordId] = updated
                        existing?.let { archiveResult.add(it) }
                    }
                // Parse DBA Funds Distributed events
                roundEvents
                    .filter { it.eventType == "B3TR_DBAFundsDistributed" }
                    .forEach { event ->
                        val appId = getAppId(event)
                        val amount = getAmountAsDecimal(event)
                        val recordId = generateId("$roundId", appId)
                        val existing = resolveExisting(recordId, updatedResult)
                        // If the amount is zero and there is an existing record, we don't need to
                        // update
                        if (amount == BigDecimal.ZERO && existing != null) {
                            return@forEach
                        }
                        val updated =
                            createOrUpdateExisting(
                                roundId = roundId,
                                appId = appId,
                                blockDetails = blockDetails,
                                existing = existing,
                                totalAmount = amount,
                            )
                        updatedResult[recordId] = updated
                        existing?.let { archiveResult.add(it) }
                    }
            }
        }

        return updatedResult.values.toList() to archiveResult
    }

    protected fun createOrUpdateExisting(
        roundId: Int,
        appId: String,
        blockDetails: BlockDetails,
        existing: XAllocResult?,
        voters: Long = 0,
        votesReceived: BigInteger = BigInteger.ZERO,
        totalAmount: BigDecimal? = null,
        unallocatedAmount: BigDecimal? = null,
        teamAllocationAmount: BigDecimal? = null,
        rewardsAllocationAmount: BigDecimal? = null,
    ): XAllocResult {
        return if (existing != null) {
            XAllocResult(
                version = existing.version + 1,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                roundId = roundId,
                appId = appId,
                voters = existing.voters + voters,
                votesReceived = existing.votesReceived + votesReceived,
                totalAmount = totalAmount ?: existing.totalAmount,
                unallocatedAmount = unallocatedAmount ?: existing.unallocatedAmount,
                teamAllocationAmount = teamAllocationAmount ?: existing.teamAllocationAmount,
                rewardsAllocationAmount =
                    rewardsAllocationAmount ?: existing.rewardsAllocationAmount,
            )
        } else {
            XAllocResult(
                version = 1,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                roundId = roundId,
                appId = appId,
                voters = voters,
                votesReceived = votesReceived,
                totalAmount = totalAmount,
                unallocatedAmount = unallocatedAmount,
                teamAllocationAmount = teamAllocationAmount,
                rewardsAllocationAmount = rewardsAllocationAmount,
            )
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<XAllocResult>, existing: List<XAllocResult>) {
        saveVersionedDocuments(
            updated,
            existing,
            repository,
            xAllocResultArchiveService,
            xAllocResultPruner,
        )
    }

    open fun isQuadraticFundingEnabled(roundId: Int): Boolean =
        cachedIsQuadraticFundingEnabled[roundId]
            ?: run {
                val clause =
                    ContractUtils.createClause(
                        address = xAllocPoolContract,
                        function = isQuadraticFundingDisabledAbi,
                        BigInteger.valueOf(roundId.toLong()),
                    )
                val response = thorService.executeReadOnlyCode(listOf(clause))
                val decoded =
                    FunctionReturnDecoder.decode(
                        response[0].data,
                        isQuadraticFundingDisabledAbi.outputs,
                    )
                val isDisabled = decoded[""] as Boolean
                cachedIsQuadraticFundingEnabled[roundId] = !isDisabled
                !isDisabled
            }

    protected fun resolveExisting(
        recordId: String,
        cache: Map<String, XAllocResult>,
    ): XAllocResult? = cache[recordId] ?: repository.findByIdOrNull(recordId)
}
