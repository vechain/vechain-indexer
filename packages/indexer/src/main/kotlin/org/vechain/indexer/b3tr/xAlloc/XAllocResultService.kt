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
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.archive.ArchiveService
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
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.IdUtils.generateId

@Profile("b3tr", "b3tr-x-alloc")
@Service
open class XAllocResultService(
    private val repository: XAllocResultRepository,
    private val xAllocResultArchiveService: ArchiveService<XAllocResult, XAllocResultArchive>,
    private val xAllocResultPruner: TargetedPruner<XAllocResult, XAllocResultArchive>,
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

    open suspend fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<XAllocResult>, List<XAllocResult>> {
        val accumulator = VersionedDocumentAccumulator<XAllocResult>(repository::findByIdOrNull)
        val bestBlockId = thorClient.getBlockUnexpanded(BlockRevision.Keyword.BEST).id

        groupByBlock(events).forEach { (blockDetails, blockEvents) ->
            accumulator.startBlock()
            groupByRoundId(blockEvents).forEach { (roundId, roundEvents) ->
                val isQFEnabled = isQuadraticFundingEnabled(roundId, bestBlockId)
                // Parse vote events
                parseVotes(
                        roundEvents.filter { it.eventType == "B3TR_XAllocationVote" },
                        isQFEnabled,
                    )
                    .forEach { (appId, aggregatedVote) ->
                        val recordId = generateId("$roundId", appId)
                        val (existing, nextVersion) = accumulator.resolve(recordId)
                        val updated =
                            addOrCreateVoteResult(
                                roundId = roundId,
                                appId = appId,
                                voters = aggregatedVote.voters,
                                votesReceived = aggregatedVote.votesReceived,
                                blockDetails = blockDetails,
                                existing = existing,
                                version = nextVersion,
                            )
                        accumulator.put(recordId, existing, updated)
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
                        val (existing, nextVersion) = accumulator.resolve(recordId)
                        val updated =
                            addOrCreateRewardClaimResult(
                                roundId = roundId,
                                appId = appId,
                                blockDetails = blockDetails,
                                existing = existing,
                                totalAmount = totalAmount,
                                unallocatedAmount = unallocatedAmount,
                                teamAllocationAmount = teamAllocationAmount,
                                rewardsAllocationAmount = rewardsAllocationAmount,
                                version = nextVersion,
                            )
                        accumulator.put(recordId, existing, updated)
                    }
                // Parse DBA Funds Distributed events
                roundEvents
                    .filter { it.eventType == "B3TR_DBAFundsDistributed" }
                    .forEach { event ->
                        val appId = getAppId(event)
                        val amount = getAmountAsDecimal(event)
                        val recordId = generateId("$roundId", appId)
                        val (existing, nextVersion) = accumulator.resolve(recordId)
                        val updated =
                            addOrCreateDbaFundResult(
                                roundId = roundId,
                                appId = appId,
                                blockDetails = blockDetails,
                                existing = existing,
                                amount = amount,
                                version = nextVersion,
                            )
                        accumulator.put(recordId, existing, updated)
                    }
            }
        }

        return accumulator.results()
    }

    protected fun addOrCreateVoteResult(
        roundId: Int,
        appId: String,
        blockDetails: BlockDetails,
        existing: XAllocResult?,
        voters: Long,
        votesReceived: BigInteger,
        version: Int,
    ): XAllocResult {
        return existing?.copy(
            version = version,
            blockId = blockDetails.blockId,
            blockNumber = blockDetails.blockNumber,
            blockTimestamp = blockDetails.blockTimestamp,
            voters = existing.voters + voters,
            votesReceived = existing.votesReceived + votesReceived,
        )
            ?: XAllocResult(
                version = version,
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
        version: Int,
    ): XAllocResult {
        return existing?.copy(
            version = version,
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
                version = version,
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
        version: Int,
    ): XAllocResult {
        return existing?.copy(
            version = version,
            blockId = blockDetails.blockId,
            blockNumber = blockDetails.blockNumber,
            blockTimestamp = blockDetails.blockTimestamp,
            totalAmount = existing.totalAmount?.plus(amount) ?: amount,
        )
            ?: XAllocResult(
                version = version,
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

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<XAllocResult>, existing: List<XAllocResult>) {
        saveVersionedDocuments(updated, existing, xAllocResultArchiveService, xAllocResultPruner)
    }

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
