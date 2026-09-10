package org.vechain.indexer.nft.backfill

import com.mongodb.client.result.UpdateResult
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.nft.NftBlacklist
import org.vechain.indexer.nft.backfill.NftBlacklistBackfillTask.Status
import org.vechain.indexer.thor.model.BlockIdentifier

@ExtendWith(MockKExtension::class)
internal class NftBlacklistBackfillServiceTest {
    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var repository: NftBlacklistBackfillRepository
    @MockK lateinit var checkpointService: CheckpointService

    private val nfts = IndexerNames.NFT.COLLECTION
    private val history = IndexerNames.HISTORY.COLLECTION
    private val contract = "0xaaaa000000000000000000000000000000000001"
    private val commits = slot<Query>()
    private val saved = slot<NftBlacklistBackfillTask>()
    private val queries = mutableListOf<Query>()

    private lateinit var service: NftBlacklistBackfillService

    @BeforeEach
    fun setUp() {
        val properties =
            NftBlacklistBackfillProperties().apply {
                batchSize = 2
                maxAttempts = 2
            }
        service =
            NftBlacklistBackfillService(mongoTemplate, repository, checkpointService, properties)
        every { mongoTemplate.findAndReplace(capture(commits), capture(saved)) } answers
            {
                secondArg()
            }
        every { mongoTemplate.collectionExists(any<String>()) } returns true
        every { checkpointService.getCheckpoint(any()) } returns BlockIdentifier(100L, null)
        every { mongoTemplate.find(capture(queries), Document::class.java, any<String>()) } returns
            emptyList()
        every { mongoTemplate.updateMulti(any<Query>(), any<Update>(), any<String>()) } returns
            UpdateResult.acknowledged(2, 2, null)
    }

    private fun task(
        isBlacklisted: Boolean = true,
        cursors: Map<String, String> = emptyMap(),
        completed: Set<String> = emptySet(),
        attempts: Int = 0,
        revision: Long = 3,
    ) =
        NftBlacklistBackfillTask(
            id = contract,
            isBlacklisted = isBlacklisted,
            requestedAtBlock = 100L,
            revision = revision,
            cursors = cursors,
            completed = completed,
            attempts = attempts,
        )

    private fun pending(vararg tasks: NftBlacklistBackfillTask) {
        every { repository.findAllByStatusOrderByUpdatedAtAsc(Status.PENDING) } returns
            tasks.toList()
    }

    private fun state(isBlacklisted: Boolean, block: Long) =
        NftBlacklist(
            id = contract,
            isBlacklisted = isBlacklisted,
            blockId = "0xb",
            blockNumber = block,
            blockTimestamp = block * 10,
            version = 3,
        )

    @Test
    fun `enqueue replaces the task with a higher revision`() {
        val tasks = slot<List<NftBlacklistBackfillTask>>()
        every { repository.findAllById(any()) } returns listOf(task(revision = 3))
        every { repository.saveAll(capture(tasks)) } answers { firstArg() }

        service.enqueue(listOf(state(isBlacklisted = false, block = 42L)))

        val task = tasks.captured.single()
        assertEquals(contract, task.id)
        assertFalse(task.isBlacklisted)
        assertEquals(42L, task.requestedAtBlock)
        assertEquals(4L, task.revision)
        assertTrue(task.cursors.isEmpty())
        assertEquals(Status.PENDING, task.status)
    }

    @Test
    fun `nothing pending means nothing to do`() {
        pending()

        assertFalse(service.runOneBatch())
    }

    @Test
    fun `a task waits until the collection's writer has passed the event block`() {
        pending(task())
        every { checkpointService.getCheckpoint(nfts) } returns BlockIdentifier(99L, null)

        assertFalse(service.runOneBatch())
        verify(exactly = 0) {
            mongoTemplate.find(any<Query>(), Document::class.java, any<String>())
        }
        verify(exactly = 0) {
            mongoTemplate.findAndReplace(any<Query>(), any<NftBlacklistBackfillTask>())
        }
    }

    @Test
    fun `an empty null-tokenId pass opens the ordered pass`() {
        pending(task())

        assertTrue(service.runOneBatch())

        val query = queries.single().queryObject
        assertEquals(contract, query["contractAddress"])
        assertEquals(Document("\$exists", true), query["blockNumber"])
        assertEquals(Document("\$ne", true), query["isBlacklisted"])
        assertTrue(query.containsKey("tokenId") && query["tokenId"] == null)
        assertEquals(mapOf(nfts to ""), saved.captured.cursors)
        assertEquals(3L, commits.captured.queryObject["revision"])
    }

    @Test
    fun `a batch flips its rows and advances the cursor`() {
        pending(task(cursors = mapOf(nfts to "")))
        every { mongoTemplate.find(capture(queries), Document::class.java, nfts) } returns
            listOf(
                Document("_id", "a").append("tokenId", "1"),
                Document("_id", "b").append("tokenId", "2"),
            )
        val ids = slot<Query>()
        val update = slot<Update>()
        every { mongoTemplate.updateMulti(capture(ids), capture(update), nfts) } returns
            UpdateResult.acknowledged(2, 2, null)

        service.runOneBatch()

        val query = queries.single()
        assertEquals(Document("\$gte", ""), query.queryObject["tokenId"])
        assertEquals(1, query.sortObject["tokenId"])
        assertEquals(2, query.limit)
        assertEquals(Document("\$in", listOf("a", "b")), ids.captured.queryObject["_id"])
        assertEquals(
            Document("\$set", Document("isBlacklisted", true)),
            update.captured.updateObject,
        )
        assertEquals(mapOf(nfts to "2"), saved.captured.cursors)
        assertEquals(2L, saved.captured.rowsUpdated)
    }

    @Test
    fun `an empty ordered pass completes the collection`() {
        pending(task(cursors = mapOf(nfts to "9")))

        service.runOneBatch()

        assertEquals(setOf(nfts), saved.captured.completed)
        assertEquals(Status.PENDING, saved.captured.status)
    }

    @Test
    fun `whitelisting only touches rows currently flagged`() {
        pending(task(isBlacklisted = false, cursors = mapOf(nfts to "")))

        service.runOneBatch()

        assertEquals(true, queries.single().queryObject["isBlacklisted"])
    }

    @Test
    fun `a missing collection is skipped`() {
        pending(task(completed = setOf(nfts)))
        every { mongoTemplate.collectionExists(history) } returns false

        service.runOneBatch()

        assertEquals(setOf(nfts, history), saved.captured.completed)
        verify(exactly = 0) { mongoTemplate.find(any<Query>(), Document::class.java, history) }
    }

    @Test
    fun `all collections completed marks the task done`() {
        pending(task(completed = setOf(nfts, history)))

        service.runOneBatch()

        assertEquals(Status.DONE, saved.captured.status)
    }

    @Test
    fun `a task replaced mid-batch keeps the newer revision`() {
        pending(task(cursors = mapOf(nfts to "")))
        every { mongoTemplate.findAndReplace(capture(commits), capture(saved)) } returns null

        assertTrue(service.runOneBatch())
    }

    @Test
    fun `failures are retried until max attempts`() {
        pending(task(attempts = 1))
        every { mongoTemplate.find(any<Query>(), Document::class.java, nfts) } throws
            IllegalStateException("boom")

        service.runOneBatch()

        assertEquals(Status.FAILED, saved.captured.status)
        assertEquals(2, saved.captured.attempts)
        assertEquals("boom", saved.captured.lastError)
    }
}
