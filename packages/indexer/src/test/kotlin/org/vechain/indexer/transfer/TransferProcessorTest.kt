package org.vechain.indexer.transfer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate

@ExtendWith(MockKExtension::class)
class TransferProcessorTest {
    @MockK lateinit var transferEventRepository: TransferEventRepository

    @MockK lateinit var mongoTemplate: MongoTemplate

    private lateinit var transferProcessor: TransferProcessor

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        transferProcessor = TransferProcessor(mongoTemplate, transferEventRepository)
    }

    @Test
    fun `process - if no events should not do anything`() {
        transferProcessor.process(emptyList(), null)

        // Verify that no interactions with mongoTemplate occur
        verify { mongoTemplate wasNot Called }
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
