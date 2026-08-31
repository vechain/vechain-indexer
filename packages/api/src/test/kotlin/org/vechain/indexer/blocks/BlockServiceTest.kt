package org.vechain.indexer.blocks

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort
import org.vechain.indexer.blocks.repository.BlockRepository
import org.vechain.indexer.exception.BadRequestException

@ExtendWith(MockKExtension::class)
internal class BlockServiceTest {

    @MockK lateinit var repository: BlockRepository

    private lateinit var service: BlockService

    @BeforeEach
    fun setUp() {
        service = BlockService(repository)
    }

    private fun indexedBlock(number: Long) =
        IndexedBlock(
            blockNumber = number,
            blockId = "0xblock-$number",
            blockTimestamp = 1_700_000_000 + number * 10,
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

    @Test
    fun `from omitted queries the head descending with the default page size`() {
        val pageableSlot = slot<Pageable>()
        every { repository.findLatest(capture(pageableSlot)) } returns
            SliceImpl(listOf(indexedBlock(1000)), PageRequest.of(0, 20), false)

        val result = service.getBlocks(null, null, null)

        assertEquals(20, pageableSlot.captured.pageSize)
        assertEquals(1, result.data.size)
        assertFalse(result.pagination.hasNext)
        assertNull(result.pagination.cursor)
    }

    @Test
    fun `from defaults to ascending and queries at or above`() {
        every { repository.findAtOrAbove(1000L, any()) } returns
            SliceImpl(listOf(indexedBlock(1000), indexedBlock(1001)), PageRequest.of(0, 2), true)

        val result = service.getBlocks(1000L, 2, null)

        verify(exactly = 1) { repository.findAtOrAbove(1000L, any()) }
        assertTrue(result.pagination.hasNext)
        assertEquals("1002", result.pagination.cursor)
    }

    @Test
    fun `from with DESC queries at or below and steps the cursor down`() {
        every { repository.findAtOrBelow(1000L, any()) } returns
            SliceImpl(listOf(indexedBlock(1000), indexedBlock(999)), PageRequest.of(0, 2), true)

        val result = service.getBlocks(1000L, 2, "DESC")

        verify(exactly = 1) { repository.findAtOrBelow(1000L, any()) }
        assertEquals("998", result.pagination.cursor)
    }

    @Test
    fun `cursor is null on the last page`() {
        every { repository.findAtOrAbove(1000L, any()) } returns
            SliceImpl(listOf(indexedBlock(1000)), PageRequest.of(0, 20), false)

        val result = service.getBlocks(1000L, null, "ASC")

        assertFalse(result.pagination.hasNext)
        assertNull(result.pagination.cursor)
    }

    @Test
    fun `a from beyond the indexed head returns an empty page`() {
        every { repository.findAtOrAbove(99_999L, any()) } returns
            SliceImpl(emptyList(), PageRequest.of(0, 20), false)

        val result = service.getBlocks(99_999L, null, "ASC")

        assertTrue(result.data.isEmpty())
        assertFalse(result.pagination.hasNext)
        assertNull(result.pagination.cursor)
    }

    @Test
    fun `an unparseable direction is rejected`() {
        assertThrows<BadRequestException> { service.getBlocks(1000L, null, "sideways") }
    }

    @Test
    fun `ascending without a from is rejected`() {
        assertThrows<BadRequestException> { service.getBlocks(null, null, "ASC") }
    }

    @Test
    fun `resolveDirection defaults by whether from is supplied`() {
        assertEquals(Sort.Direction.DESC, service.resolveDirection(null, null))
        assertEquals(Sort.Direction.ASC, service.resolveDirection(1000L, null))
        assertEquals(Sort.Direction.DESC, service.resolveDirection(1000L, "desc"))
    }
}
