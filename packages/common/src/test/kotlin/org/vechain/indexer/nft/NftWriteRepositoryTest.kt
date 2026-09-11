package org.vechain.indexer.nft

import org.apache.commons.codec.digest.DigestUtils
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NftWriteRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: NftWriteRepository

    private val contract = "0x" + "c".repeat(40)
    private val alice = "0x" + "a".repeat(40)
    private val bob = "0x" + "b".repeat(40)
    private val carol = "0x" + "d".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        writer = NftWriteRepository(database.jdbc)
    }

    @AfterAll fun stop() = database.close()

    @BeforeEach fun reset() = writer.truncate()

    private fun nft(tokenId: String, owner: String, block: Long, tx: Int = block.toInt()) =
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

    /** Every row as "token:block:owner:supersededAt", ordered. */
    private fun rows(): List<String> =
        database.jdbc.queryForList(
            "SELECT token_id::text || ':' || block_number || ':' || encode(owner, 'hex') || ':' || " +
                "coalesce(superseded_at::text, '-') FROM nft.ownership ORDER BY token_id, block_number",
            String::class.java,
        )

    private fun current(): List<IndexedNft> =
        database.jdbc
            .query(
                "SELECT * FROM nft.ownership WHERE superseded_at IS NULL ORDER BY token_id",
                { rs, _ -> NftRowMapping.row(rs) },
            )
            .map(NftRowMapping::assemble)

    @Test
    fun `a mint and later transfers leave one current owner per token`() {
        writer.save(listOf(nft("1", alice, 10), nft("2", alice, 10)))
        writer.save(listOf(nft("1", bob, 20)))

        assertEquals(listOf(nft("1", bob, 20), nft("2", alice, 10)), current())
        assertEquals(
            listOf(
                "1:10:${"a".repeat(40)}:20",
                "1:20:${"b".repeat(40)}:-",
                "2:10:${"a".repeat(40)}:-",
            ),
            rows(),
        )
    }

    @Test
    fun `several transfers of one token in one block keep the last`() {
        writer.save(listOf(nft("1", alice, 10, tx = 1), nft("1", bob, 10, tx = 2)))

        assertEquals(listOf(nft("1", bob, 10, tx = 2)), current())
        assertEquals(1, database.count("nft.ownership"))
    }

    @Test
    fun `rollback at depth restores the owner of every touched block`() {
        writer.save(listOf(nft("1", alice, 10)))
        writer.save(listOf(nft("1", bob, 20)))
        writer.save(listOf(nft("1", carol, 30), nft("2", carol, 30)))

        writer.rollbackFrom(20)

        assertEquals(listOf(nft("1", alice, 10)), current())
        assertEquals(listOf("1:10:${"a".repeat(40)}:-"), rows())
    }

    @Test
    fun `replaying a block changes nothing`() {
        writer.save(listOf(nft("1", alice, 10)))
        writer.save(listOf(nft("1", bob, 20)))
        val before = rows()

        writer.save(listOf(nft("1", bob, 20)))
        writer.save(listOf(nft("1", alice, 10)))

        assertEquals(before, rows())
    }

    @Test
    fun `prune keeps the current rows and those superseded inside the window`() {
        writer.save(listOf(nft("1", alice, 10)))
        writer.save(listOf(nft("1", bob, 20)))
        writer.save(listOf(nft("1", carol, 30)))

        writer.prune(before = 25)

        assertEquals(listOf("1:20:${"b".repeat(40)}:30", "1:30:${"d".repeat(40)}:-"), rows())
        assertEquals(listOf(nft("1", carol, 30)), current())
    }

    @Test
    fun `truncate empties the table`() {
        writer.save(listOf(nft("1", alice, 10)))
        writer.truncate()
        assertTrue(rows().isEmpty())
    }
}
