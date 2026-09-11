package org.vechain.indexer.nft

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NftBlacklistWriteRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: NftBlacklistWriteRepository
    private lateinit var reader: NftBlacklistReadRepository

    private val a = "0x" + "a".repeat(40)
    private val b = "0x" + "b".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        writer = NftBlacklistWriteRepository(database.jdbc)
        reader = NftBlacklistReadRepository(database.jdbc)
    }

    @AfterAll fun stop() = database.close()

    @BeforeEach fun reset() = writer.truncate()

    private fun state(contract: String, block: Long, flagged: Boolean) =
        NftBlacklistState(
            contractAddress = contract,
            isBlacklisted = flagged,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
        )

    /** Every row as (contract, block, flagged, supersededAt). */
    private fun rows(): List<List<Any?>> =
        database.jdbc.query(
            "SELECT contract_address, block_number, is_blacklisted, superseded_at " +
                "FROM nft_blacklist.collection_state ORDER BY contract_address, block_number"
        ) { rs, _ ->
            listOf(
                "0x" + rs.getBytes(1).joinToString("") { "%02x".format(it) },
                rs.getLong(2),
                rs.getBoolean(3),
                rs.getObject(4),
            )
        }

    @Test
    fun `an empty schema knows no collection`() {
        assertNull(reader.current(a))
        assertTrue(reader.blacklisted().isEmpty())
    }

    @Test
    fun `two events for one collection in one block collapse onto the last`() {
        writer.save(listOf(state(a, 10, true), state(a, 10, false), state(b, 10, true)))

        assertEquals(listOf(listOf(a, 10L, false, null), listOf(b, 10L, true, null)), rows())
        assertEquals(listOf(b), reader.blacklisted())
    }

    @Test
    fun `a flip closes the open row and the current state follows`() {
        writer.save(listOf(state(a, 10, true)))
        writer.save(listOf(state(a, 20, false)))

        assertEquals(listOf(listOf(a, 10L, true, 20L), listOf(a, 20L, false, null)), rows())
        assertFalse(reader.current(a)!!.isBlacklisted)
        assertEquals(20L, reader.current(a)!!.blockNumber)
    }

    @Test
    fun `blocks in one save apply in ascending order whatever the list order`() {
        writer.save(listOf(state(a, 20, false), state(a, 10, true)))

        assertEquals(listOf(listOf(a, 10L, true, 20L), listOf(a, 20L, false, null)), rows())
    }

    @Test
    fun `rollback across a flip puts the earlier state back`() {
        writer.save(listOf(state(a, 10, true), state(b, 10, true)))
        writer.save(listOf(state(a, 20, false)))

        writer.rollbackFrom(20)

        assertEquals(listOf(listOf(a, 10L, true, null), listOf(b, 10L, true, null)), rows())
        assertEquals(listOf(a, b), reader.blacklisted())
    }

    @Test
    fun `rollback to before the first state leaves nothing`() {
        writer.save(listOf(state(a, 10, true)))
        writer.save(listOf(state(a, 20, false)))

        writer.rollbackFrom(10)

        assertTrue(rows().isEmpty())
    }

    @Test
    fun `replaying a block changes nothing`() {
        writer.save(listOf(state(a, 10, true)))
        writer.save(listOf(state(a, 20, false)))
        val before = rows()

        writer.save(listOf(state(a, 20, false)))
        assertEquals(before, rows())

        writer.save(listOf(state(a, 10, true)))
        assertEquals(before, rows())
    }

    @Test
    fun `prune drops rows superseded before the bound and keeps the open ones`() {
        writer.save(listOf(state(a, 10, true)))
        writer.save(listOf(state(a, 20, false)))
        writer.save(listOf(state(a, 30, true)))

        writer.prune(before = 25)

        assertEquals(listOf(listOf(a, 20L, false, 30L), listOf(a, 30L, true, null)), rows())
    }

    @Test
    fun `states carry normalised addresses`() {
        assertThrows(IllegalArgumentException::class.java) { state(a.uppercase(), 1, true) }
    }
}
