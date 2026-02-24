package org.vechain.indexer.pruner

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.util.CloseableIterator
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.archive.ArchiveService

@ExtendWith(MockKExtension::class)
internal class PrunerTest {

    @MockK lateinit var archiveService: ArchiveService<MyVersionedDocument, MyArchive>

    private lateinit var pruner: PrunerService<MyVersionedDocument, MyArchive>
    private lateinit var disabledPruner: PrunerService<MyVersionedDocument, MyArchive>

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        pruner = PrunerService(MyArchive::class, archiveService, 2)
        disabledPruner = PrunerService(MyArchive::class, archiveService, 2, enabled = false)
    }

    @Test
    fun `should skip if not enough blocks to prune`() {
        pruner.run(9_000)

        verify(exactly = 0) { archiveService.findRecordsToPrune(any(), any()) }
        verify(exactly = 0) { archiveService.removeAll(any()) }
    }

    @Test
    fun `should skip if no records to prune`() {
        every { archiveService.findRecordsToPrune(any(), any()) } returns iteratorOf()

        pruner.run(50_000)

        verify(exactly = 1) { archiveService.findRecordsToPrune(any(), eq(2)) }
        verify(exactly = 0) { archiveService.removeAll(any()) }
    }

    @Test
    fun `should prune records in chunks`() {
        every { archiveService.findRecordsToPrune(any(), any()) } returns
            iteratorOf("1", "2", "3", "4", "5")
        every { archiveService.removeAll(any()) } just Runs

        pruner.run(50_000)

        verify(exactly = 1) { archiveService.findRecordsToPrune(any(), eq(2)) }
        verify(exactly = 3) { archiveService.removeAll(any()) }
    }

    @Test
    fun `should skip pruning when disabled even with pruneable records`() {
        every { archiveService.findRecordsToPrune(any(), any()) } returns
            iteratorOf("1", "2", "3", "4", "5")
        every { archiveService.removeAll(any()) } just Runs

        disabledPruner.run(50_000)

        verify(exactly = 0) { archiveService.findRecordsToPrune(any(), any()) }
        verify(exactly = 0) { archiveService.removeAll(any()) }
    }

    @Test
    fun `should skip pruning with targeted ids when disabled`() {
        every { archiveService.findRecordsToPrune(any(), any(), any()) } returns
            iteratorOf("1", "2", "3")
        every { archiveService.removeAll(any()) } just Runs

        disabledPruner.run(50_000, listOf("id1", "id2"))

        verify(exactly = 0) { archiveService.findRecordsToPrune(any(), any(), any()) }
        verify(exactly = 0) { archiveService.removeAll(any()) }
    }

    @Test
    fun `enabled flag controls pruning - enabled prunes, disabled does not`() {
        every { archiveService.findRecordsToPrune(any(), any()) } returns iteratorOf("1", "2", "3")
        every { archiveService.removeAll(any()) } just Runs

        val enabledPruner = PrunerService(MyArchive::class, archiveService, 2, enabled = true)
        enabledPruner.run(50_000)

        verify(exactly = 1) { archiveService.findRecordsToPrune(any(), eq(2)) }
        verify(exactly = 2) { archiveService.removeAll(any()) }

        clearMocks(archiveService)

        disabledPruner.run(50_000)

        verify(exactly = 0) { archiveService.findRecordsToPrune(any(), any()) }
        verify(exactly = 0) { archiveService.removeAll(any()) }
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

private fun iteratorOf(vararg elements: String): CloseableIterator<String> =
    object : CloseableIterator<String> {
        private val delegate = elements.iterator()

        override fun close() {}

        override fun hasNext(): Boolean = delegate.hasNext()

        override fun next(): String = delegate.next()

        override fun remove() {
            throw UnsupportedOperationException("remove is not supported")
        }
    }
