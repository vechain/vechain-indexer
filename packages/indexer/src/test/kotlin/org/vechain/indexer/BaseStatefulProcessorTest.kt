package org.vechain.indexer

import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

@ExtendWith(MockKExtension::class)
class BaseStatefulProcessorTest {
    @MockK lateinit var repository: BaseIndexedRepository<TestDocument, String>

    @MockK lateinit var archiveService: ArchiveService<TestDocument, TestDocumentArchive>

    private lateinit var processor: TestableBaseStatefulProcessor

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        processor = TestableBaseStatefulProcessor(repository, archiveService)
    }

    @Test
    fun `rollback - deletes blocks in range`() {
        every { archiveService.rollback(10) } just Runs

        processor.rollback(10)

        verify(exactly = 1) { archiveService.rollback(10) }
    }

    data class TestDocument(
        override val blockId: String,
        override val blockNumber: Long,
        override val blockTimestamp: Long,
        override val version: Int,
    ) : VersionedDocument {
        override fun getDocumentId(): String {
            return "$blockId-$blockNumber-$version"
        }
    }

    data class TestDocumentArchive(override val id: String, override val data: TestDocument) :
        Archive<TestDocument>

    class TestableBaseStatefulProcessor(
        repository: BaseIndexedRepository<*, *>,
        archiveService: ArchiveService<*, *>,
    ) : BaseStatefulProcessor(repository, archiveService) {

        override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
            // does nothing
        }
    }
}
