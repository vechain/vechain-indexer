package org.vechain.indexer.nft.backfill

import java.time.Instant
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.nft.NftBlacklist
import org.vechain.indexer.nft.backfill.NftBlacklistBackfillTask.Status

/**
 * Flips `isBlacklisted` on existing rows one bounded batch per tick, in `tokenId` order through the
 * `contractAddress, tokenId` index. Rows already at the target flag are skipped, so a batch is
 * idempotent and a task resumes from its cursor; replacing a task resets the cursors.
 */
@Profile("nfts", "history")
@Service
open class NftBlacklistBackfillService(
    private val mongoTemplate: MongoTemplate,
    private val repository: NftBlacklistBackfillRepository,
    private val checkpointService: CheckpointService,
    private val properties: NftBlacklistBackfillProperties,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        val TARGET_COLLECTIONS =
            listOf(IndexerNames.NFT.COLLECTION, IndexerNames.HISTORY.COLLECTION)
        const val FLAG_FIELD = "isBlacklisted"
        const val CONTRACT_FIELD = "contractAddress"
        const val TOKEN_ID_FIELD = "tokenId"
        const val BLOCK_NUMBER_FIELD = "blockNumber"
    }

    open fun enqueue(states: List<NftBlacklist>) {
        if (states.isEmpty()) return
        val current = repository.findAllById(states.map { it.id }).associateBy { it.id }
        repository.saveAll(
            states.map {
                NftBlacklistBackfillTask(
                    id = it.id,
                    isBlacklisted = it.isBlacklisted,
                    requestedAtBlock = it.blockNumber,
                    revision = (current[it.id]?.revision ?: 0L) + 1,
                )
            }
        )
    }

    @Scheduled(
        fixedDelayString = "\${indexer.blacklist.backfill.tick-ms:250}",
        initialDelayString = "\${indexer.blacklist.backfill.initial-delay-ms:30000}",
    )
    open fun tick() {
        if (!properties.enabled) return
        try {
            runOneBatch()
        } catch (e: Exception) {
            logger.error("NFT blacklist backfill tick failed", e)
        }
    }

    /** Advances the oldest task with a ready collection by one batch; false if none is ready. */
    open fun runOneBatch(): Boolean {
        for (task in repository.findAllByStatusOrderByUpdatedAtAsc(Status.PENDING)) {
            val collection = TARGET_COLLECTIONS.firstOrNull { it !in task.completed }
            if (collection == null) {
                logger.info(
                    "NFT blacklist backfill for {} done: isBlacklisted={} rows={}",
                    task.id,
                    task.isBlacklisted,
                    task.rowsUpdated,
                )
                commit(task, task.copy(status = Status.DONE))
                return true
            }
            if (!mongoTemplate.collectionExists(collection)) {
                commit(task, task.copy(completed = task.completed + collection))
                return true
            }
            if (!writerHasPassed(collection, task.requestedAtBlock)) continue
            commit(task, step(task, collection))
            return true
        }
        return false
    }

    /** Later writes see the new state once the writer has passed the event block. */
    private fun writerHasPassed(collection: String, block: Long): Boolean =
        (checkpointService.getCheckpoint(collection)?.number ?: -1L) >= block

    private fun step(task: NftBlacklistBackfillTask, collection: String): NftBlacklistBackfillTask =
        try {
            advance(task, collection)
        } catch (e: Exception) {
            val attempts = task.attempts + 1
            logger.warn("NFT blacklist backfill for {} failed (attempt {})", task.id, attempts, e)
            task.copy(
                attempts = attempts,
                lastError = e.message,
                status = if (attempts >= properties.maxAttempts) Status.FAILED else Status.PENDING,
            )
        }

    /** Progress lands only on the revision the batch started from; a newer task wins. */
    private fun commit(from: NftBlacklistBackfillTask, next: NftBlacklistBackfillTask) {
        val query = Query(Criteria.where("_id").`is`(from.id).and("revision").`is`(from.revision))
        if (mongoTemplate.findAndReplace(query, next.copy(updatedAt = Instant.now())) == null) {
            logger.info(
                "NFT blacklist backfill for {} was replaced mid-batch; dropping progress",
                from.id,
            )
        }
    }

    private fun advance(
        task: NftBlacklistBackfillTask,
        collection: String,
    ): NftBlacklistBackfillTask {
        val cursor = task.cursors[collection]
        val rows = mongoTemplate.find(batchQuery(task, cursor), Document::class.java, collection)
        if (rows.isEmpty()) {
            // The null-tokenId pass is over: start the ordered pass, or finish the collection.
            return if (cursor == null) task.copy(cursors = task.cursors + (collection to ""))
            else task.copy(completed = task.completed + collection)
        }
        val result =
            mongoTemplate.updateMulti(
                Query(Criteria.where("_id").`in`(rows.map { it["_id"] })),
                Update().set(FLAG_FIELD, task.isBlacklisted),
                collection,
            )
        val cursors =
            if (cursor == null) task.cursors
            else task.cursors + (collection to rows.last().getString(TOKEN_ID_FIELD))
        return task.copy(rowsUpdated = task.rowsUpdated + result.modifiedCount, cursors = cursors)
    }

    private fun batchQuery(task: NftBlacklistBackfillTask, cursor: String?): Query {
        val flag = Criteria.where(FLAG_FIELD)
        return Query().apply {
            addCriteria(Criteria.where(CONTRACT_FIELD).`is`(task.id))
            addCriteria(Criteria.where(BLOCK_NUMBER_FIELD).exists(true))
            // Whitelisting only flips rows that are true; reads already ignore null and false.
            addCriteria(if (task.isBlacklisted) flag.ne(true) else flag.`is`(true))
            if (cursor == null) {
                addCriteria(Criteria.where(TOKEN_ID_FIELD).`is`(null))
            } else {
                addCriteria(Criteria.where(TOKEN_ID_FIELD).gte(cursor))
                with(Sort.by(TOKEN_ID_FIELD))
            }
            limit(properties.batchSize)
            fields().include(TOKEN_ID_FIELD)
        }
    }
}
