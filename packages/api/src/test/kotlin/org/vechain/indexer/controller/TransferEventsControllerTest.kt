package org.vechain.indexer.controller

import org.junit.jupiter.api.Nested
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
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `get transfer events with path param should return NOT_FOUND`() {
        mockMvc.get("$baseEndpoint/pathParam")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `get transfer events with valid address should return OK`() {
        val page = 0
        val size = Int.MAX_VALUE
        val result = mockMvc.get(
            "$baseEndpoint?address=0x0f872421dc479f3c11edd89512731814d0598db5" +
                    "&page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val transferEvents = objectMapper.readValue(result.response.contentAsString, LIST_TRANSFER_EVENT_TYPE)

        expectThat(transferEvents).hasSize(12)
    }

    @Test
    fun `get transfer events address with no hex prefix should return OK`() {
        val page = 0
        val size = Int.MAX_VALUE
        val result = mockMvc.get(
            "$baseEndpoint?address=0f872421dc479f3c11edd89512731814d0598db5" +
                    "&page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val transferEvents = objectMapper.readValue(result.response.contentAsString, LIST_TRANSFER_EVENT_TYPE)

        expectThat(transferEvents).hasSize(12)
    }

    @Test
    fun `get transfer events address uppercase should return OK`() {
        val page = 0
        val size = Int.MAX_VALUE
        val result = mockMvc.get(
            "$baseEndpoint?address=0x0F872421dc479f3c11edd89512731814D0598db5" +
                    "&page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val transferEvents = objectMapper.readValue(result.response.contentAsString, LIST_TRANSFER_EVENT_TYPE)

        expectThat(transferEvents).hasSize(12)
    }

    @Test
    fun `get transfer events with paginated result`() {
        val page = 1
        val size = 5
        val result = mockMvc.get(
            "$baseEndpoint?address=0x0f872421dc479f3c11edd89512731814d0598db5" +
                    "&page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val transferEvents = objectMapper.readValue(result.response.contentAsString, LIST_TRANSFER_EVENT_TYPE)

        expectThat(transferEvents).hasSize(size)
    }

    @Test
    fun `check transfer events is sorted by block number & txId & id`() {
        val page = 0
        val size = 3
        val result = mockMvc.get(
            "$baseEndpoint?address=0x0f872421dc479f3c11edd89512731814d0598db5" +
                    "&page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val transferEvents = objectMapper.readValue(result.response.contentAsString, LIST_TRANSFER_EVENT_TYPE)

        expectThat(transferEvents)
            .hasSize(size)
            .isSorted(
                compareByDescending<TransferEvent> { it.blockNumber }
                    .then(compareByDescending<TransferEvent> { it.txId }
                        .then(compareByDescending { it.id })
                    )
            )
    }

    @Test
    fun `get transfer events for contract`() {
        val page = 0
        val size = Int.MAX_VALUE
        val result = mockMvc.get(
            "$baseEndpoint?tokenAddress=0x08f30373569af024d15eb47fd477a35db929eaac" +
                    "&page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val transferEvents = objectMapper.readValue(result.response.contentAsString, LIST_TRANSFER_EVENT_TYPE)

        expectThat(transferEvents).hasSize(70)
    }

    @Test
    fun `get transfer events for contract no hex prefix`() {
        val page = 0
        val size = Int.MAX_VALUE
        val result = mockMvc.get(
            "$baseEndpoint?tokenAddress=0x08f30373569af024d15eb47fd477a35db929eaac" +
                    "&page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val transferEvents = objectMapper.readValue(result.response.contentAsString, LIST_TRANSFER_EVENT_TYPE)

        expectThat(transferEvents).hasSize(70)
    }

    @Test
    fun `get transfer events for contract upper case`() {
        val page = 0
        val size = Int.MAX_VALUE
        val result = mockMvc.get(
            "$baseEndpoint?tokenAddress=${"0x08f30373569af024d15eb47fd477a35db929eaac".uppercase()}" +
                    "&page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val transferEvents = objectMapper.readValue(result.response.contentAsString, LIST_TRANSFER_EVENT_TYPE)

        expectThat(transferEvents).hasSize(70)
    }


    @Nested
    inner class FromTransferEvents {
        @Test
        fun `get transfer events for from address`() {
            val page = 0
            val size = Int.MAX_VALUE
            val result = mockMvc.get(
                "$baseEndpoint/from?address=0x0f872421dc479f3c11edd89512731814d0598db5" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transferEvents = objectMapper.readValue(result.response.contentAsString, LIST_TRANSFER_EVENT_TYPE)

            expectThat(transferEvents).hasSize(4)
        }

        @Test
        fun `get transfer events with contract address`() {
            val page = 0
            val size = Int.MAX_VALUE
            val result = mockMvc.get(
                "$baseEndpoint/from?address=0x0f872421dc479f3c11edd89512731814d0598db5" + "&tokenAddress=0x08f30373569af024d15eb47fd477a35db929eaac" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transferEvents = objectMapper.readValue(result.response.contentAsString, LIST_TRANSFER_EVENT_TYPE)

            expectThat(transferEvents).hasSize(1)
        }
    }

    @Nested
    inner class ToTransferEvents {
        @Test
        fun `get transfer events for to address`() {
            val page = 0
            val size = Int.MAX_VALUE
            val result = mockMvc.get(
                "$baseEndpoint/to?address=0x0f872421dc479f3c11edd89512731814d0598db5" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transferEvents = objectMapper.readValue(result.response.contentAsString, LIST_TRANSFER_EVENT_TYPE)

            expectThat(transferEvents).hasSize(8)
        }

        @Test
        fun `get transfer events with to and contract address`() {
            val page = 0
            val size = Int.MAX_VALUE
            val result = mockMvc.get(
                "$baseEndpoint/to?address=0x0f872421dc479f3c11edd89512731814d0598db5" +
                        "&tokenAddress=0x08f30373569af024d15eb47fd477a35db929eaac" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transferEvents = objectMapper.readValue(result.response.contentAsString, LIST_TRANSFER_EVENT_TYPE)

            expectThat(transferEvents).hasSize(2)
        }
    }

}