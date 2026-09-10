package org.vechain.indexer.nft

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
internal class NftBlacklistLookupTest {
    @MockK lateinit var repository: NftBlacklistRepository

    private val flagged = "0xAAAA000000000000000000000000000000000001"
    private val cleared = "0xBBBB000000000000000000000000000000000002"

    @Test
    fun `asks once for the normalised ids and keeps only flagged ones`() {
        val asked = slot<Iterable<String>>()
        every { repository.findAllById(capture(asked)) } returns
            listOf(state(flagged.lowercase(), true), state(cleared.lowercase(), false))

        val result = NftBlacklistLookup(repository).blacklisted(listOf(flagged, cleared, flagged))

        assertEquals(setOf(flagged.lowercase(), cleared.lowercase()), asked.captured.toSet())
        assertTrue(flagged in result)
        assertTrue(flagged.lowercase() in result)
        assertFalse(cleared in result)
    }

    @Test
    fun `no addresses means no query`() {
        val result = NftBlacklistLookup(repository).blacklisted(emptyList())

        assertFalse(flagged in result)
        verify(exactly = 0) { repository.findAllById(any()) }
    }

    private fun state(id: String, isBlacklisted: Boolean) =
        NftBlacklist(
            id = id,
            isBlacklisted = isBlacklisted,
            blockId = "0xblock",
            blockNumber = 1L,
            blockTimestamp = 10L,
            version = 1,
        )
}
