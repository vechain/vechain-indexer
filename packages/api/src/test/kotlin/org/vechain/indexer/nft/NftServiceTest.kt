package org.vechain.indexer.nft

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.thor.Address

class NftServiceTest {
    private val repository: NftReadRepository = mockk()
    private val service = NftService(repository)
    private val owner = Address("0x" + "a".repeat(40))
    private val contract = Address("0x" + "1".repeat(40))

    private fun nft(id: String) =
        IndexedNft(id, "1", contract.value, owner.value, "0x01", 1, "0x02", 10)

    @Test
    fun `owned nfts page with one row past the page, in the requested direction`() {
        val pageable = PageRequest.of(1, 2, Sort.by(Direction.ASC, "blockNumber", "txId", "_id"))
        every {
            repository.findByOwner(owner.value, listOf(contract.value), 2L, 3, Direction.ASC)
        } returns listOf(nft("1"), nft("2"), nft("3"))

        val slice = service.findOwnedNfts(owner, null, null, listOf(contract), pageable)

        assertEquals(listOf("1", "2"), slice.content.map { it.id })
        assertTrue(slice.hasNext())
    }

    @Test
    fun `a collection filter normalises a hex token id to base 10`() {
        val pageable = PageRequest.of(0, 20, Sort.by(Direction.DESC, "blockNumber"))
        every {
            repository.findByOwnerAndContract(
                owner.value,
                contract.value,
                "42",
                0L,
                21,
                Direction.DESC,
            )
        } returns listOf(nft("1"))

        val slice = service.findOwnedNfts(owner, contract, "0x2a", null, pageable)

        assertEquals(1, slice.content.size)
        assertFalse(slice.hasNext())
    }

    @Test
    fun `an empty token id means no token filter`() {
        val pageable = PageRequest.of(0, 20, Sort.by(Direction.DESC, "blockNumber"))
        every {
            repository.findByOwnerAndContract(
                owner.value,
                contract.value,
                null,
                0L,
                21,
                Direction.DESC,
            )
        } returns emptyList()

        assertTrue(service.findOwnedNfts(owner, contract, "", null, pageable).content.isEmpty())
    }

    @Test
    fun `contracts forward the exclusions and paging`() {
        val pageable = PageRequest.of(0, 1, Sort.by(Direction.DESC, "blockNumber"))
        every {
            repository.findContractsByOwner(owner.value, emptyList(), 0L, 2, Direction.DESC)
        } returns listOf(contract.value, "0x" + "2".repeat(40))

        val slice = service.findContractsByNftOwner(owner, null, pageable)

        assertEquals(listOf(contract.value), slice.content)
        assertTrue(slice.hasNext())
    }
}
