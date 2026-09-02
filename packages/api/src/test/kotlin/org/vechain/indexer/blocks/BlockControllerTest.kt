package org.vechain.indexer.blocks

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpHeaders
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.PaginationDetail

@ExtendWith(MockKExtension::class)
internal class BlockControllerTest {

    @MockK lateinit var blockService: BlockService

    private lateinit var controller: BlockController

    @BeforeEach
    fun setUp() {
        controller = BlockController(blockService)
    }

    private fun range(settledAt: Long?) =
        BlockRange(
            page = PaginatedResponse(emptyList<IndexedBlock>(), PaginationDetail(true, "999")),
            settledAt = settledAt,
        )

    private fun cacheControl(settledAt: Long?): String? {
        every { blockService.getBlocks(any(), any()) } returns range(settledAt)
        return controller.getBlocks(1000L, null).headers.getFirst(HttpHeaders.CACHE_CONTROL)
    }

    @Test
    fun `a settled page is cacheable for as long as it has been settled`() {
        val anHourAgo = Instant.now().epochSecond - 3600

        val maxAge = cacheControl(anHourAgo)?.substringAfter("max-age=")?.toLong()

        // Wall-clock bound rather than exact: the age is computed against the real clock.
        assertTrue(maxAge != null && maxAge in 3600L..3700L, "unexpected age: $maxAge")
    }

    @Test
    fun `a page that can still change gets the short shared TTL`() {
        assertEquals(CachePolicy.VOLATILE.headerValue, cacheControl(null))
    }

    @Test
    fun `the page is returned as the response body`() {
        val expected = range(1_700_000_000L)
        every { blockService.getBlocks(1000L, 50) } returns expected

        val result = controller.getBlocks(1000L, 50)

        verify(exactly = 1) { blockService.getBlocks(1000L, 50) }
        assertEquals(200, result.statusCode.value())
        assertEquals(expected.page, result.body)
    }
}
