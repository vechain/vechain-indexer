package org.vechain.indexer.blocks

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.SliceImpl
import org.vechain.indexer.blocks.repository.BlockRepository

@ExtendWith(MockKExtension::class)
internal class BlockServiceTest {

    @MockK lateinit var repository: BlockRepository

    private lateinit var service: BlockService

    @BeforeEach
    fun setUp() {
        service = BlockService(repository)
    }

    /** Block N is stamped at [BASE_TIMESTAMP] + N * 10, matching Thor's 10s block time. */
    private fun indexedBlock(number: Long) =
        IndexedBlock(
            blockNumber = number,
            blockId = "0xblock-$number",
            blockTimestamp = BASE_TIMESTAMP + number * 10,
            size = 361,
            parentID = "0xblock-${number - 1}",
            gasLimit = 40_000_000,
            gasUsed = 0,
            beneficiary = "0xbeneficiary",
            totalScore = number,
            txsRoot = "0xtxsRoot",
            txsFeatures = 1,
            stateRoot = "0xstateRoot",
            receiptsRoot = "0xreceiptsRoot",
            com = true,
            signer = "0xsigner",
        )

    private fun stampOf(number: Long) = BASE_TIMESTAMP + number * 10

    // -- query routing and cursors --

    @Test
    fun `from omitted queries the head with the default page size`() {
        val pageableSlot = slot<Pageable>()
        every { repository.findLatest(capture(pageableSlot)) } returns
            SliceImpl(listOf(indexedBlock(1000)), PageRequest.of(0, 20), false)

        val result = service.getBlocks(null, null)

        assertEquals(20, pageableSlot.captured.pageSize)
        assertEquals(1, result.page.data.size)
        assertFalse(result.page.pagination.hasNext)
        assertNull(result.page.pagination.cursor)
    }

    @Test
    fun `from bounds the range above and steps the cursor down`() {
        every { repository.findAtOrBelow(1000L, any()) } returns
            SliceImpl(listOf(indexedBlock(1000), indexedBlock(999)), PageRequest.of(0, 2), true)

        val result = service.getBlocks(1000L, 2)

        verify(exactly = 1) { repository.findAtOrBelow(1000L, any()) }
        assertTrue(result.page.pagination.hasNext)
        assertEquals("998", result.page.pagination.cursor)
    }

    @Test
    fun `the head page cursor continues the same backwards walk`() {
        every { repository.findLatest(any()) } returns
            SliceImpl(listOf(indexedBlock(999), indexedBlock(998)), PageRequest.of(0, 2), true)

        assertEquals("997", service.getBlocks(null, 2).page.pagination.cursor)
    }

    @Test
    fun `cursor is null on the last page`() {
        every { repository.findAtOrBelow(1000L, any()) } returns
            SliceImpl(listOf(indexedBlock(1000)), PageRequest.of(0, 20), false)

        val result = service.getBlocks(1000L, null)

        assertFalse(result.page.pagination.hasNext)
        assertNull(result.page.pagination.cursor)
    }

    @Test
    fun `a from below the indexed range returns an empty page`() {
        every { repository.findAtOrBelow(5L, any()) } returns
            SliceImpl(emptyList(), PageRequest.of(0, 20), false)

        val result = service.getBlocks(5L, null)

        assertTrue(result.page.data.isEmpty())
        assertFalse(result.page.pagination.hasNext)
        assertNull(result.page.pagination.cursor)
    }

    // -- getBlocks wires the cache decision through --

    @Test
    fun `a head range is not cacheable`() {
        every { repository.findLatest(any()) } returns
            SliceImpl(listOf(indexedBlock(1000)), PageRequest.of(0, 1), true)

        assertNull(service.getBlocks(null, 1).settledAt)
    }

    @Test
    fun `a settled anchored range is cacheable`() {
        every { repository.findAtOrBelow(1000L, any()) } returns
            SliceImpl(listOf(indexedBlock(1000), indexedBlock(999)), PageRequest.of(0, 2), true)

        assertNotNull(service.getBlocks(1000L, 2).settledAt)
    }

    // -- settledAt: the structural gate, with the grading left to the endpoint --

    @Test
    fun `the head range never settles however old its newest row looks`() {
        // The backfill case: an ancient indexed head must not read as settled.
        assertNull(service.settledAt(null, listOf(indexedBlock(1000))))
    }

    @Test
    fun `an empty page does not settle`() {
        assertNull(service.settledAt(1000L, emptyList()))
    }

    @Test
    fun `a page anchored above the indexed head does not settle`() {
        val data = listOf(indexedBlock(1000), indexedBlock(999))

        assertNull(service.settledAt(5000L, data))
    }

    @Test
    fun `a page with a missing record does not settle`() {
        // 998 is absent, so the span is 4 but only 3 rows came back.
        val data = listOf(indexedBlock(1000), indexedBlock(999), indexedBlock(997))

        assertNull(service.settledAt(1000L, data))
    }

    @Test
    fun `a contiguous anchored page settles at its newest row`() {
        val data = listOf(indexedBlock(1000), indexedBlock(999), indexedBlock(998))

        assertEquals(stampOf(1000), service.settledAt(1000L, data))
    }

    @Test
    fun `a short page at the genesis end is still contiguous and settles`() {
        val data = (30L downTo 0L).map(::indexedBlock)

        assertEquals(stampOf(30), service.settledAt(30L, data))
    }

    @Test
    fun `a single row page is contiguous`() {
        val data = listOf(indexedBlock(1000))

        assertEquals(stampOf(1000), service.settledAt(1000L, data))
    }

    companion object {
        private const val BASE_TIMESTAMP = 1_700_000_000L
    }
}
