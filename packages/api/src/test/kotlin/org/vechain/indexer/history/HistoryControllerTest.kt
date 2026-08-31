package org.vechain.indexer.history

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.Page
import org.springframework.http.HttpHeaders
import org.vechain.indexer.rest.VOLATILE_CACHE_CONTROL
import org.vechain.indexer.thor.Address

@ExtendWith(MockKExtension::class)
internal class HistoryControllerTest {

    @MockK lateinit var historyService: HistoryService

    private lateinit var controller: HistoryController

    private val account = Address("0x0000000000000000000000000000000000000001")

    @BeforeEach
    fun setUp() {
        controller = HistoryController(historyService)
    }

    @Test
    fun `account history is cacheable for a block, not indefinitely`() {
        every {
            historyService.findUserHistoryByFilters(any(), any(), any(), any(), any(), any(), any())
        } returns Page.empty<IndexedHistoryEvent>()

        val result =
            controller.getUsersHistoryV2(
                account = account,
                eventName = null,
                searchBy = null,
                contractAddress = null,
                after = null,
                before = null,
                page = null,
                size = null,
                direction = null,
            )

        assertEquals(VOLATILE_CACHE_CONTROL, result.headers.getFirst(HttpHeaders.CACHE_CONTROL))
    }

    @Test
    fun `token history is cacheable for a block, not indefinitely`() {
        every {
            historyService.findTokenIdHistoryByFilters(any(), any(), any(), any(), any(), any())
        } returns Page.empty<IndexedHistoryEvent>()

        val result =
            controller.getTokenHistory(
                tokenId = "1",
                eventName = null,
                contractAddress = null,
                after = null,
                before = null,
                page = null,
                size = null,
                direction = null,
            )

        assertEquals(VOLATILE_CACHE_CONTROL, result.headers.getFirst(HttpHeaders.CACHE_CONTROL))
    }
}
