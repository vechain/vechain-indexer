package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.VersionedDocument
import org.vechain.indexer.pruner.Pruner
import org.vechain.indexer.repository.BaseIndexedRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block

@ExtendWith(MockKExtension::class)
internal class StatefulBlockIndexerTest {
    @MockK lateinit var repository: BaseIndexedRepository<*>

    @MockK lateinit var thorClient: ThorClient

    @MockK
    lateinit var archiveService: ArchiveService<VersionedDocument, Archive<VersionedDocument>>

    @MockK lateinit var pruner: Pruner<VersionedDocument, Archive<VersionedDocument>>

    private lateinit var statefulIndexer:
        StatefulBlockIndexer<VersionedDocument, Archive<VersionedDocument>>

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        statefulIndexer =
            spyk(
                object :
                    StatefulBlockIndexer<VersionedDocument, Archive<VersionedDocument>>(
                        repository,
                        0L,
                        thorClient,
                        1000L,
                        10000,
                        archiveService,
                        pruner,
                    ) {
                    override fun processBlock(block: Block) {
                        // do nothing
                    }
                }
            )
    }

    @Test
    fun `rollback should call archiveService rollback`() {
        val blockNumber = 100L

        every { archiveService.rollback(blockNumber) } just Runs

        statefulIndexer.rollback(blockNumber)

        verify(exactly = 1) { archiveService.rollback(blockNumber) }
    }
}
