package org.vechain.indexer.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.vechain.indexer.AbstractIntegrationTest
import org.vechain.indexer.constants.TRANSFER_EVENTS_PATH
import org.vechain.indexer.model.TransferEvent
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isSorted

internal class TransferEventsControllerTest : AbstractIntegrationTest() {

    companion object {
        const val baseEndpoint = TRANSFER_EVENTS_PATH
        const val TRANSFERS_TOTAL_NUMBER = 80
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `get transfer events with path param should return NOT_FOUND`() {
        mockMvc.get("$baseEndpoint/pathParam")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `get all transfer events with valid endpoint should return OK`() {
        val page = 0
        val size = Int.MAX_VALUE
        val result = mockMvc.get(
            baseEndpoint +
                    "?page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val transferEvents = objectMapper.readValue(result.response.contentAsString, TRANSFER_EVENT_TYPE)

        expectThat(transferEvents).hasSize(TRANSFERS_TOTAL_NUMBER)
    }

    @Test
    fun `get all transfer events with paginated result`() {
        val page = 3
        val size = 25
        val result = mockMvc.get(
            baseEndpoint +
                    "?page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val transferEvents = objectMapper.readValue(result.response.contentAsString, TRANSFER_EVENT_TYPE)

        expectThat(transferEvents).hasSize(TRANSFERS_TOTAL_NUMBER - (page * size))
    }

    @Test
    fun `get all transfer events is sorted by block number & txId & id`() {
        val page = 2
        val size = 20
        val result = mockMvc.get(
            baseEndpoint +
                    "?page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val transferEvents = objectMapper.readValue(result.response.contentAsString, TRANSFER_EVENT_TYPE)

        expectThat(transferEvents)
            .hasSize(size)
            .isSorted(
                compareByDescending<TransferEvent> { it.blockNumber }
                    .then(compareByDescending<TransferEvent> { it.txId }
                        .then(compareByDescending { it.id })
                    )
            )
    }

}