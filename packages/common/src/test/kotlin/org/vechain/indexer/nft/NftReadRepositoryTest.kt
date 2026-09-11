package org.vechain.indexer.nft

import org.apache.commons.codec.digest.DigestUtils
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction.ASC
import org.springframework.data.domain.Sort.Direction.DESC
import org.vechain.indexer.postgres.PostgresTestDatabase

/** One test per API query on a seeded owner; see [seed] for who holds what, and what is flagged. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NftReadRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var repository: NftReadRepository

    private val alice = "0x" + "a".repeat(40)
    private val bob = "0x" + "b".repeat(40)
    private val art = "0x" + "1".repeat(40)
    private val cards = "0x" + "c".repeat(40)
    private val spam = "0x" + "3".repeat(40)
    private val seeded = mutableMapOf<String, IndexedNft>()

    @BeforeAll
    fun start() {
        database.start()
        repository = NftReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    private fun nft(contract: String, tokenId: String, owner: String, block: Long, tx: Int) =
        IndexedNft(
            id = DigestUtils.sha1Hex("$contract-$tokenId"),
            tokenId = tokenId,
            contractAddress = contract,
            owner = owner,
            txId = "0x" + tx.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockTimestamp = block * 10,
        )

    /** alice: art 1 and 2 (blocks 10 and 30), cards 5 (block 20), spam 9 (block 40, flagged). */
    private fun seed() {
        val nfts = NftWriteRepository(database.jdbc)
        fun put(key: String, n: IndexedNft) = nfts.save(listOf(n)).also { seeded[key] = n }
        // art 2 was bob's first: a superseded row that no read may see
        nfts.save(listOf(nft(art, "2", bob, 5, 6)))
        put("art1", nft(art, "1", alice, 10, 1))
        put("cards5", nft(cards, "5", alice, 20, 2))
        put("art2", nft(art, "2", alice, 30, 3))
        put("spam9", nft(spam, "9", alice, 40, 4))
        put("bob", nft(cards, "6", bob, 40, 5))
        NftBlacklistWriteRepository(database.jdbc)
            .save(listOf(NftBlacklistState(spam, true, "0x" + "f".repeat(64), 41, 410)))
    }

    private fun ids(vararg keys: String) = keys.map { seeded.getValue(it).id }

    @Test
    fun `owned nfts page newest first, skip the flagged collection and honour direction`() {
        val desc = repository.findByOwner(alice, emptyList(), 0, 10, DESC)
        assertEquals(ids("art2", "cards5", "art1"), desc.map { it.id })
        assertEquals(seeded["art2"], desc.first())

        val asc = repository.findByOwner(alice, emptyList(), 1, 1, ASC)
        assertEquals(ids("cards5"), asc.map { it.id })
    }

    @Test
    fun `excluded collections drop out of the listing`() {
        val page = repository.findByOwner(alice, listOf("0x" + "C".repeat(40)), 0, 10, DESC)
        assertEquals(ids("art2", "art1"), page.map { it.id })
    }

    @Test
    fun `a collection filter, with or without a token id, goes through the current owner`() {
        assertEquals(
            ids("art2", "art1"),
            repository.findByOwnerAndContract(alice, art, null, 0, 10, DESC).map { it.id },
        )
        assertEquals(
            ids("art1"),
            repository.findByOwnerAndContract(alice, art, "1", 0, 10, DESC).map { it.id },
        )
        assertTrue(repository.findByOwnerAndContract(bob, art, "2", 0, 10, DESC).isEmpty())
        assertTrue(repository.findByOwnerAndContract(alice, spam, null, 0, 10, DESC).isEmpty())
    }

    @Test
    fun `contracts list the owner's collections by newest acquisition, flagged ones aside`() {
        assertEquals(
            listOf(art, cards),
            repository.findContractsByOwner(alice, emptyList(), 0, 10, DESC),
        )
        assertEquals(
            listOf(cards),
            repository.findContractsByOwner(alice, emptyList(), 1, 10, DESC),
        )
        assertEquals(listOf(cards), repository.findContractsByOwner(alice, listOf(art), 0, 10, ASC))
        assertEquals(listOf(cards), repository.findContractsByOwner(bob, emptyList(), 0, 10, DESC))
    }

    @Test
    fun `findAll is every current row, including the flagged collection`() {
        assertEquals(5, repository.findAll().size)
        assertTrue(repository.findAll().none { it.owner == bob && it.contractAddress == art })
    }
}
