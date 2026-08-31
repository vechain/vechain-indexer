package org.vechain.indexer.blocks

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpHeaders
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

    private fun response(hasNext: Boolean, cursor: String? = null) =
        PaginatedResponse(emptyList<IndexedBlock>(), PaginationDetail(hasNext, cursor))

    private fun cacheControl(from: Long?, hasNext: Boolean): String? {
        every { blockService.getBlocks(from, any(), any()) } returns response(hasNext)
        return controller.getBlocks(from, null, null).headers.getFirst(HttpHeaders.CACHE_CONTROL)
    }

    @Test
    fun `a full page from a numeric anchor is immutable`() {
        assertEquals(IMMUTABLE_CACHE_CONTROL, cacheControl(from = 1000L, hasNext = true))
    }

    @Test
    fun `a head range gets the short shared TTL`() {
        assertEquals(HEAD_CACHE_CONTROL, cacheControl(from = null, hasNext = true))
    }

    @Test
    fun `the last page from a numeric anchor gets the short shared TTL`() {
        assertEquals(HEAD_CACHE_CONTROL, cacheControl(from = 1000L, hasNext = false))
    }

    @Test
    fun `parameters are forwarded to the service unchanged`() {
        every { blockService.getBlocks(1000L, 50, "DESC") } returns response(hasNext = false)

        val result = controller.getBlocks(1000L, 50, "DESC")

        verify(exactly = 1) { blockService.getBlocks(1000L, 50, "DESC") }
        assertEquals(200, result.statusCode.value())
    }
}
