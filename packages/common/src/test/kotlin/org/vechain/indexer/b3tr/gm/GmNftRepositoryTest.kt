package org.vechain.indexer.b3tr.gm

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.postgres.IndexBuilder
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.thor.Address

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GmNftRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: GmNftWriteRepository
    private lateinit var reader: GmNftReadRepository

    private val alice = "0x" + "a".repeat(40)
    private val bob = "0x" + "b".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        writer = GmNftWriteRepository(database.jdbc)
        reader = GmNftReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    /** Alice mints 1 and 2, upgrades 1 to MOON at block 20 and burns 2 at block 30. */
    private fun seed() {
        writer.save(
            listOf(
                nft("1", 10, alice),
                nft("2", 10, alice),
                nft("3", 10, bob),
                nft("1", 20, alice, GmLevelName.MOON, node = "77"),
                nft("2", 30, Address.ZERO_ADDRESS),
            )
        )
    }

    private fun nft(
        tokenId: String,
        block: Long,
        owner: String,
        level: GmLevelName = GmLevelName.EARTH,
        node: String? = null,
    ) =
        GmNft(
            tokenId = tokenId,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            level = level,
            attachedNodeId = node,
            b3trDonated = BigInteger.valueOf(block),
            owner = owner,
        )

    @Test
    fun `a token round-trips and only its newest row is current`() {
        assertEquals(
            listOf(nft("1", 20, alice, GmLevelName.MOON, node = "77"), nft("3", 10, bob)),
            writer.findCurrentByTokenIds(setOf("1", "3")).sortedBy { it.tokenId },
        )
    }

    @Test
    fun `the level counts skip a burned token`() {
        assertEquals(
            listOf(GMLevelOverview(GmLevelName.EARTH, 1), GMLevelOverview(GmLevelName.MOON, 1)),
            reader.levelCounts().sortedBy { it.level },
        )
        assertEquals(1L, reader.countByLevel(GmLevelName.EARTH))
        assertEquals(0L, reader.countByLevel(GmLevelName.GALAXY))
    }

    @Test
    fun `the write path holds up with the level index dropped`() {
        val builder = IndexBuilder(database.properties)
        builder.drop(GmNftIndexes.SET)
        try {
            writer.save(listOf(nft("1", 40, bob, GmLevelName.MARS)))

            assertEquals(40L, writer.findCurrentByTokenIds(setOf("1")).single().blockNumber)

            writer.rollbackFrom(40)
            assertEquals(20L, writer.findCurrentByTokenIds(setOf("1")).single().blockNumber)
        } finally {
            builder.build(GmNftIndexes.SET)
        }
    }

    @Test
    fun `rollback reopens the superseded rows and truncate empties the table`() {
        writer.rollbackFrom(20)
        assertEquals(3L, reader.levelCounts().sumOf { it.totalNFTs })

        writer.truncate()
        assertEquals(emptyList<GMLevelOverview>(), reader.levelCounts())
        seed()
    }

    @Test
    fun `prune drops superseded rows below the horizon and reports how many`() {
        assertEquals(1, writer.prune(21))
        assertEquals(GmLevelName.MOON, writer.findCurrentByTokenIds(setOf("1")).single().level)
        writer.truncate()
        seed()
    }
}
