package org.vechain.indexer.history

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.Page
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy
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
    fun `account history is served without blowing up on an empty page`() {
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

        assertNotNull(result)
    }

    @Test
    fun `history tolerates a stale minute, which is where its caching gain sits`() {
        // Measured: 7.9% of requests repeat within 10s, 12.0% within a minute. `before` closes the
        // window and would grade higher, but no caller sends it, so a flat minute is the ceiling.
        assertEquals(CachePolicy.MINUTE, declaredCachePolicy("getUsersHistory"))
        assertEquals(CachePolicy.MINUTE, declaredCachePolicy("getUsersHistoryV2"))
    }

    private fun declaredCachePolicy(method: String): CachePolicy? =
        HistoryController::class
            .java
            .methods
            .single { it.name == method }
            .getAnnotation(CacheFor::class.java)
            ?.policy
}
