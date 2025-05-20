package org.vechain.indexer.pruner

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.vechain.indexer.Status
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.VersionedDocument
import org.vechain.indexer.service.ArchiveService

@ExtendWith(MockKExtension::class)
internal class PrunerTest {

    @MockK lateinit var archiveService: ArchiveService<MyVersionedDocument, MyArchive>

    private lateinit var pruner: Pruner<MyVersionedDocument, MyArchive>

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        pruner = Pruner(MyArchive::class, archiveService, 2)
    }

    @Test
    fun `should skip if syncing`() {
        pruner.prune(1, Status.SYNCING)

        verify(exactly = 0) { archiveService.findRecordsToPrune(any()) }
        verify(exactly = 0) { archiveService.removeAll(any()) }
    }

    @Test
    fun `should skip if in reorg state`() {
        pruner.prune(1, Status.REORG)

        verify(exactly = 0) { archiveService.findRecordsToPrune(any()) }
        verify(exactly = 0) { archiveService.removeAll(any()) }
    }

    @Test
    fun `should skip if in error state`() {
        pruner.prune(1, Status.ERROR)

        verify(exactly = 0) { archiveService.findRecordsToPrune(any()) }
        verify(exactly = 0) { archiveService.removeAll(any()) }
    }

    @Test
    fun `should skip if not enough blocks to prune`() {
        pruner.prune(9_000, Status.FULLY_SYNCED)

        verify(exactly = 0) { archiveService.findRecordsToPrune(any()) }
        verify(exactly = 0) { archiveService.removeAll(any()) }
    }

    @Test
    fun `should skip if no records to prune`() {
        every { archiveService.findRecordsToPrune(any()) } returns emptyList()

        pruner.prune(50_000, Status.FULLY_SYNCED)

        verify(exactly = 1) { archiveService.findRecordsToPrune(any()) }
        verify(exactly = 0) { archiveService.removeAll(any()) }
    }

    @Test
    fun `should prune records in chunks`() {
        every { archiveService.findRecordsToPrune(any()) } returns listOf("1", "2", "3", "4", "5")
        every { archiveService.removeAll(any()) } just Runs

        pruner.prune(50_000, Status.FULLY_SYNCED)

        verify(exactly = 1) { archiveService.findRecordsToPrune(any()) }
        verify(exactly = 3) { archiveService.removeAll(any()) }
    }
}

// VersionedDocument and ArchiveClass for testing purposes
data class MyVersionedDocument
@ConstructorBinding
constructor(
    override val blockId: String,
    override val version: Int,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
) : VersionedDocument {
    override fun getDocumentId(): String {
        return blockId
    }
}

data class MyArchive(override val id: String, override val data: MyVersionedDocument) :
    Archive<MyVersionedDocument>
