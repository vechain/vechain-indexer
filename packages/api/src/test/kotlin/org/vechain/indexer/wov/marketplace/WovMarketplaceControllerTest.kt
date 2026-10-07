package org.vechain.indexer.wov.marketplace

import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.PaginationDetail

internal class WovMarketplaceControllerTest {
    private val service = mockk<WovBuyerStatsService>()
    private val controller = WovMarketplaceController(service)

    private val hourAgo = Instant.now().epochSecond - 3_600

    private fun cacheControl(to: Long, cursor: String?): String? {
        every { service.buyers(0, to, null, cursor) } returns
            PaginatedResponse(emptyList(), PaginationDetail(false))
        return controller.getBuyers(0, to, null, cursor).headers.getFirst(HttpHeaders.CACHE_CONTROL)
    }

    @Test
    fun `a window is cached for as long as it has been closed`() {
        assertEquals("public, max-age=3600", cacheControl(hourAgo, null))
        assertEquals("public, max-age=3600", cacheControl(hourAgo, "$hourAgo|0xab"))
        assertEquals(
            CachePolicy.VOLATILE.headerValue,
            cacheControl(Instant.now().epochSecond, null),
        )
    }
}
