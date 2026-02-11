package org.vechain.indexer.transfer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.checkpoint.CheckpointService

@ExtendWith(MockKExtension::class)
class TransferProcessorTest {
    @MockK lateinit var transferService: TransferService

    @MockK lateinit var transferEventRepository: TransferEventRepository

    @MockK lateinit var checkpointService: CheckpointService

    private lateinit var transferProcessor: TransferProcessor

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        transferProcessor =
            TransferProcessor(transferService, transferEventRepository, checkpointService)
    }

    @Test
    fun `process - if no events should not do anything`() {
        runBlocking {
            transferProcessor.process(
                IndexingResult.EventsOnly(
                    events = emptyList(),
                    endBlock = 100,
                    status = Status.SYNCING,
                )
            )
        }

        // Verify that no interactions with transferService occur
        verify { transferService wasNot Called }
    }

    //    @Test
    //    fun `process - should call to mongoTemplate when transfer events are present`() {
    //
    //        every {
    //            mongoTemplate.insert(any<IndexedTransferEvent>(),
    // eq(IndexedTransferEvent::class.java))
    //        } returns mockk()
    //
    //        transferProcessor.process(INDEXED_EVENTS_TRANSFERS, BLOCK_TRANSFERS)
    //
    //        // Verify that mongoTemplate.insert was called
    //        verify { mongoTemplate }
    //    }

}
