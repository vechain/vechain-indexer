package org.vechain.indexer

import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.thor.model.BlockIdentifier
import org.vechain.indexer.version.IndexerVersionService

@ExtendWith(MockKExtension::class)
class BaseProcessorTest {

    @MockK lateinit var repository: BaseIndexedRepository<Document, String>

    @MockK lateinit var indexerVersionService: IndexerVersionService

    private lateinit var processor: TestableBaseProcessor

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        processor = TestableBaseProcessor(repository, indexerVersionService)
    }

    @Test
    fun `rollback - deletes blocks starting from requested height`() {
        every { repository.deleteAllByBlockNumberGreaterThanEqual(9) } just Runs

        processor.rollback(10)

        verify { repository.deleteAllByBlockNumberGreaterThanEqual(9) }
    }

    @Test
    fun `getLastSyncedBlock returns null when neither source has data`() {
        every { repository.getLatestRecord() } returns null
        every { indexerVersionService.getLastProcessedBlock(TEST_INDEXER_NAME) } returns null

        val result = processor.getLastSyncedBlock()

        assertNull(result)
    }

    @Test
    fun `getLastSyncedBlock returns repository block when version service is empty`() {
        val document = Document(blockId = "repo", blockNumber = 42L, blockTimestamp = 0L)
        every { repository.getLatestRecord() } returns document
        every { indexerVersionService.getLastProcessedBlock(TEST_INDEXER_NAME) } returns null

        val result = processor.getLastSyncedBlock()

        assertEquals("repo", result?.id)
        assertEquals(42L, result?.number)
    }

    @Test
    fun `getLastSyncedBlock returns version service block when repository is empty`() {
        val versionBlock = BlockIdentifier(id = "version", number = 99L)
        every { repository.getLatestRecord() } returns null
        every { indexerVersionService.getLastProcessedBlock(TEST_INDEXER_NAME) } returns
            versionBlock

        val result = processor.getLastSyncedBlock()

        assertSame(versionBlock, result)
    }

    @Test
    fun `getLastSyncedBlock returns block with latest height`() {
        val repoDocument = Document(blockId = "repo", blockNumber = 100L, blockTimestamp = 0L)
        val versionBlock = BlockIdentifier(id = "version", number = 90L)

        every { repository.getLatestRecord() } returns repoDocument
        every { indexerVersionService.getLastProcessedBlock(TEST_INDEXER_NAME) } returns
            versionBlock

        val result = processor.getLastSyncedBlock()

        assertEquals("repo", result?.id)
        assertEquals(100L, result?.number)
    }

    @Test
    fun `getLastSyncedBlock prefers version service when it is ahead`() {
        val repoDocument = Document(blockId = "repo", blockNumber = 50L, blockTimestamp = 0L)
        val versionBlock = BlockIdentifier(id = "version", number = 99L)

        every { repository.getLatestRecord() } returns repoDocument
        every { indexerVersionService.getLastProcessedBlock(TEST_INDEXER_NAME) } returns
            versionBlock

        val result = processor.getLastSyncedBlock()

        assertSame(versionBlock, result)
    }

    data class Document(
        override val blockId: String,
        override val blockNumber: Long,
        override val blockTimestamp: Long,
    ) : IndexedDocument

    class TestableBaseProcessor(
        repository: BaseIndexedRepository<*, *>,
        indexerVersionService: IndexerVersionService,
    ) : BaseProcessor(repository, indexerVersionService, TEST_INDEXER_NAME) {

        override fun process(entry: IndexingResult) {
            // no-op for tests
        }
    }

    companion object {
        private const val TEST_INDEXER_NAME = "TestIndexer"
    }
}
