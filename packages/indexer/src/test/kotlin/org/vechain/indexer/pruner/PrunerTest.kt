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
import org.vechain.indexer.archive.ArchiveService

@ExtendWith(MockKExtension::class)
internal class PrunerTest {

    @MockK lateinit var archiveService: ArchiveService<MyVersionedDocument>

    private lateinit var pruner: PrunerService<MyVersionedDocument>

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { archiveService.clazz } returns MyVersionedDocument::class.java
        pruner = PrunerService(archiveService, 2)
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
}

// VersionedDocument for testing purposes
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
