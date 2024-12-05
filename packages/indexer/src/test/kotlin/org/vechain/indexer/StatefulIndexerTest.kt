package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_NO_CLAUSES
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.VersionedDocument
import org.vechain.indexer.repository.BaseIndexedRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block

class StatefulIndexerTest {

    @MockK lateinit var repository: BaseIndexedRepository<TestDoc>
    @MockK lateinit var thorClient: ThorClient
    @MockK lateinit var archiveService: ArchiveService<TestDoc, Archive<TestDoc>>
    private lateinit var indexer: TestStatefulIndexer

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    fun `processBlock should save and archive records and run pruner`() {
        val extractDataResponse = listOf("data")
        val findExistingResponse = listOf(TestDoc(1, "1", 1L, 1L))
        val parseRecordsResponse = listOf(TestDoc(2, "2", 2L, 2L))

        indexer =
            TestStatefulIndexer(
                repository,
                0L,
                thorClient,
                1000L,
                true,
                1000L,
                archiveService,
                extractDataResponse,
                findExistingResponse,
                parseRecordsResponse
            )

        val block = BLOCK_NO_CLAUSES.copy(number = 3000L)

        every { archiveService.prune(any()) } just Runs
        every { archiveService.update(any<List<TestDoc>>(), any<List<TestDoc>>()) } returns Unit

        indexer.processBlock(block)

        verify(exactly = 1) { archiveService.prune(block.number) }
        verify(exactly = 1) { archiveService.update(parseRecordsResponse, findExistingResponse) }
    }

    @Test
    fun `processBlock should save and archive records but not run pruner`() {
        val extractDataResponse = listOf("data")
        val findExistingResponse = listOf(TestDoc(1, "1", 1L, 1L))
        val parseRecordsResponse = listOf(TestDoc(2, "2", 2L, 2L))

        indexer =
            TestStatefulIndexer(
                repository,
                0L,
                thorClient,
                1000L,
                false,
                1000L,
                archiveService,
                extractDataResponse,
                findExistingResponse,
                parseRecordsResponse
            )

        val block = BLOCK_NO_CLAUSES.copy(number = 3000L)

        every { archiveService.prune(any()) } just Runs
        every { archiveService.update(any<List<TestDoc>>(), any<List<TestDoc>>()) } returns Unit

        indexer.processBlock(block)

        verify(exactly = 0) { archiveService.prune(block.number) }
        verify(exactly = 1) { archiveService.update(parseRecordsResponse, findExistingResponse) }
    }

    @Test
    fun `processBlock should save new records, no archives, should not prune`() {
        val extractDataResponse = listOf("data")
        val findExistingResponse = emptyList<TestDoc>()
        val parseRecordsResponse = listOf(TestDoc(2, "2", 2L, 2L))

        indexer =
            TestStatefulIndexer(
                repository,
                0L,
                thorClient,
                1000L,
                true,
                1000L,
                archiveService,
                extractDataResponse,
                findExistingResponse,
                parseRecordsResponse
            )

        val block = BLOCK_NO_CLAUSES.copy(number = 3001L)

        every { archiveService.prune(any()) } just Runs
        every { archiveService.update(any<List<TestDoc>>(), any<List<TestDoc>>()) } returns Unit

        indexer.processBlock(block)

        verify(exactly = 0) { archiveService.prune(block.number) }
        verify(exactly = 1) { archiveService.update(parseRecordsResponse, findExistingResponse) }
    }

    @Test
    fun `processBlock no new records, no archives, should prune`() {
        val extractDataResponse = emptyList<String>()
        val findExistingResponse = emptyList<TestDoc>()
        val parseRecordsResponse = emptyList<TestDoc>()

        indexer =
            TestStatefulIndexer(
                repository,
                0L,
                thorClient,
                1000L,
                true,
                1000L,
                archiveService,
                extractDataResponse,
                findExistingResponse,
                parseRecordsResponse
            )

        val block = BLOCK_NO_CLAUSES.copy(number = 3000L)

        every { archiveService.prune(any()) } just Runs
        every { archiveService.update(any<List<TestDoc>>(), any<List<TestDoc>>()) } returns Unit

        indexer.processBlock(block)

        verify(exactly = 1) { archiveService.prune(block.number) }
        verify(exactly = 0) { archiveService.update(parseRecordsResponse, findExistingResponse) }
    }

    data class TestDoc
    @ConstructorBinding
    constructor(
        override var version: Int,
        override var blockId: String,
        override val blockNumber: Long,
        override val blockTimestamp: Long,
    ) : VersionedDocument {
        override fun getDocumentId(): String {
            return blockId
        }
    }

    inner class TestStatefulIndexer(
        repository: BaseIndexedRepository<TestDoc>,
        startBlock: Long,
        thorClient: ThorClient,
        syncLogInterval: Long,
        prunerEnabled: Boolean,
        prunerInterval: Long,
        archiveService: ArchiveService<TestDoc, Archive<TestDoc>>,
        val extractDataResponse: List<String>,
        val findExistingResponse: List<TestDoc>,
        val parseRecordsResponse: List<TestDoc>
    ) :
        StatefulIndexer<TestDoc, Archive<TestDoc>, String>(
            repository,
            startBlock,
            thorClient,
            syncLogInterval,
            prunerEnabled,
            prunerInterval,
            archiveService
        ) {

        override fun extractData(block: Block): List<String> {
            return extractDataResponse
        }

        override fun findExisting(data: List<String>): List<TestDoc> {
            return findExistingResponse
        }

        override fun parseRecords(
            block: Block,
            data: List<String>,
            existing: List<TestDoc>
        ): List<TestDoc> {
            return parseRecordsResponse
        }
    }
}
