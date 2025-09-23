package org.vechain.indexer.b3tr.xAlloc

import java.math.BigInteger
import kotlin.collections.component1
import kotlin.collections.component2
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.Pruner
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.IdUtils.generateId
import org.vechain.indexer.b3tr.xAlloc.XAllocEventUtils.groupByRoundId
import org.vechain.indexer.b3tr.xAlloc.XAllocEventUtils.parseVotes
import org.vechain.indexer.b3tr.xAlloc.repository.XAllocResultRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock

@Profile("b3tr", "b3tr-x-alloc")
@Service
open class XAllocResultService(
    private val repository: XAllocResultRepository,
    private val xAllocResultArchiveService: ArchiveService<XAllocResult, XAllocResultArchive>,
    private val xAllocResultPruner: Pruner,
) {

    open fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<XAllocResult>, List<XAllocResult>> {
        val updatedResult = mutableMapOf<String, XAllocResult>()
        val archiveResult = mutableListOf<XAllocResult>()

        groupByBlock(events).forEach { (blockDetails, blockEvents) ->
            groupByRoundId(blockEvents).forEach { (roundId, roundEvents) ->
                parseVotes(roundEvents).forEach { (appId, aggregatedVote) ->
                    val recordId = generateId("$roundId", appId)
                    val existing = resolveExisting(recordId, updatedResult)
                    val updated =
                        createOrUpdateExisting(
                            roundId = roundId,
                            appId = appId,
                            voters = aggregatedVote.voters,
                            totalVotes = aggregatedVote.weight,
                            blockDetails = blockDetails,
                            existing = existing,
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
        voters: Long,
        totalVotes: BigInteger,
        blockDetails: BlockDetails,
        existing: XAllocResult?,
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
                totalVotes = existing.totalVotes + totalVotes,
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
                totalVotes = totalVotes,
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

    protected fun resolveExisting(
        recordId: String,
        cache: Map<String, XAllocResult>,
    ): XAllocResult? = cache[recordId] ?: repository.findByIdOrNull(recordId)
}
