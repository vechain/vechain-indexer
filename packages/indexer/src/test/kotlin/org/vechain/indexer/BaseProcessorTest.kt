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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class BaseProcessorTest {

    @MockK lateinit var repository: BaseIndexedRepository<Document, String>

    private lateinit var processor: TestableBaseProcessor

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        processor = TestableBaseProcessor(repository)
    }

    @Test
    fun `rollback - deletes blocks starting from requested height`() {
        every { repository.deleteAllByBlockNumberGreaterThanEqual(10) } just Runs

        processor.rollback(10)

        verify { repository.deleteAllByBlockNumberGreaterThanEqual(10) }
    }

    @Test
    fun `getLastSyncedBlock returns null when repository is empty`() {
        every { repository.getLatestRecord() } returns null

        val result = processor.getLastSyncedBlock()

        assertNull(result)
    }

    @Test
    fun `getLastSyncedBlock returns repository block`() {
        val document = Document(blockId = "repo", blockNumber = 42L, blockTimestamp = 0L)
        every { repository.getLatestRecord() } returns document

        val result = processor.getLastSyncedBlock()

        assertEquals("repo", result?.id)
        assertEquals(42L, result?.number)
    }

    data class Document(
        override val blockId: String,
        override val blockNumber: Long,
        override val blockTimestamp: Long,
    ) : IndexedDocument

    class TestableBaseProcessor(repository: BaseIndexedRepository<*, *>) :
        BaseProcessor(repository, TEST_INDEXER_NAME) {

        override suspend fun processEntry(entry: IndexingResult) {
            // no-op for tests
        }
    }

    companion object {
        private const val TEST_INDEXER_NAME = "TestIndexer"
    }
}
