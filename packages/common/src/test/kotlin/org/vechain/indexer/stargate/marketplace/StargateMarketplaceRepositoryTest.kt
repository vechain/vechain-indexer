package org.vechain.indexer.stargate.marketplace

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.postgres.IndexBuilder
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.stargate.token.StargateToken
import org.vechain.indexer.stargate.token.StargateTokenWriteRepository
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.Address

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StargateMarketplaceRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: StargateMarketplaceWriteRepository
    private lateinit var reader: StargateMarketplaceReadRepository

    private val alice = "0x" + "aa".repeat(20)
    private val bob = "0x" + "bb".repeat(20)
    private val carol = "0x" + "cc".repeat(20)
    private val dave = "0x" + "dd".repeat(20)
    private val vet = Address.ZERO_ADDRESS
    private val wov = "0x" + "11".repeat(20)

    @BeforeAll
    fun start() {
        database.start()
        writer = StargateMarketplaceWriteRepository(database.jdbc)
        reader = StargateMarketplaceReadRepository(database.jdbc)
        seedTokens()
        seedListings()
    }

    @AfterAll fun stop() = database.close()

    /** Token 3 is burned and 4 transferred to carol at block 40; 7 goes to carol when sold. */
    private fun seedTokens() {
        val tokens = StargateTokenWriteRepository(database.jdbc)
        tokens.save(
            listOf(
                token("1", 5, alice),
                token("2", 5, bob, TokenLevel.Flash),
                token("3", 5, alice),
                token("4", 5, alice),
                token("5", 5, dave),
                token("7", 5, bob),
            )
        )
        tokens.save(
            listOf(
                token("3", 40, Address.ZERO_ADDRESS),
                token("4", 40, carol),
                token("7", 40, carol),
            )
        )
    }

    /**
     * Block 10 lists tokens 1 to 7 (6 has no state row, 5 is priced in WoV); block 20 re-lists
     * token 1 as sale 8; block 30 sells token 7.
     */
    private fun seedListings() {
        writer.save(
            listOf(
                listing(1, 10, 1, alice, 100),
                listing(2, 10, 2, bob, 300),
                listing(3, 10, 3, alice, 50),
                listing(4, 10, 4, alice, 200),
                listing(5, 10, 6, alice, 10),
                listing(6, 10, 5, dave, 20, token = wov),
                listing(9, 10, 7, bob, 400),
            )
        )
        writer.save(
            listOf(
                listing(1, 20, 1, alice, 100, ListingStatus.CANCELLED),
                listing(8, 20, 1, alice, 90),
            )
        )
        writer.save(listOf(listing(9, 30, 7, bob, 400, ListingStatus.SOLD, buyer = carol)))
    }

    private fun reseed() {
        writer.truncate()
        seedListings()
    }

    @Test
    fun `save closes a sale's previous row and leaves the newest current`() {
        assertEquals(
            listOf(
                listing(1, 20, 1, alice, 100, ListingStatus.CANCELLED),
                listing(9, 30, 7, bob, 400, ListingStatus.SOLD, buyer = carol),
            ),
            writer.findCurrent(ids(1, 9, 42)),
        )
        assertEquals(emptyList<StargateListing>(), writer.findCurrent(emptyList()))
        assertEquals(10, database.count(StargateListingRowMapping.TABLE))
        assertEquals(
            1,
            database.count("${StargateListingRowMapping.TABLE} WHERE superseded_at = 20"),
        )
    }

    @Test
    fun `a replayed block neither closes its own rows nor duplicates them`() {
        writer.save(
            listOf(
                listing(1, 20, 1, alice, 100, ListingStatus.CANCELLED),
                listing(8, 20, 1, alice, 90),
            )
        )

        assertEquals(listOf(listing(8, 20, 1, alice, 90)), writer.findCurrent(ids(8)))
        assertEquals(10, database.count(StargateListingRowMapping.TABLE))
    }

    @Test
    fun `the valid page excludes burned, transferred, unknown and non-VET tokens`() {
        assertEquals(listOf(8L, 2L), valid(ListingSort.NEWEST, Direction.DESC).map(::saleId))
        assertEquals(listOf(2L, 8L), valid(ListingSort.NEWEST, Direction.ASC).map(::saleId))
        assertEquals(listOf(8L, 2L), valid(ListingSort.PRICE, Direction.ASC).map(::saleId))
        assertEquals(listOf(2L, 8L), valid(ListingSort.PRICE, Direction.DESC).map(::saleId))
        assertEquals(
            listOf(2L),
            valid(ListingSort.NEWEST, Direction.DESC, offset = 1, limit = 1).map(::saleId),
        )
    }

    @Test
    fun `the valid page narrows by level and seller and carries the token's state`() {
        val flash = valid(ListingSort.NEWEST, Direction.DESC, level = TokenLevel.Flash)
        assertEquals(listOf(2L), flash.map(::saleId))
        assertEquals(TokenLevel.Flash, flash.single().level)
        assertEquals(BigInteger.TEN, flash.single().vetStaked)
        assertEquals(bob, flash.single().owner)

        assertEquals(
            listOf(8L),
            valid(ListingSort.NEWEST, Direction.DESC, seller = alice).map(::saleId),
        )
        assertEquals(
            emptyList<Long>(),
            valid(ListingSort.NEWEST, Direction.DESC, seller = carol).map(::saleId),
        )
    }

    @Test
    fun `the by-token read is the open listing with whatever state the token has`() {
        val relisted = reader.findOpenByToken("1")!!
        assertEquals(8L, saleId(relisted))
        assertEquals(alice, relisted.owner)
        assertEquals(TokenLevel.Dawn, relisted.level)

        assertEquals(Address.ZERO_ADDRESS, reader.findOpenByToken("3")?.owner)
        assertEquals(carol, reader.findOpenByToken("4")?.owner)
        assertEquals(wov, reader.findOpenByToken("5")?.listing?.paymentToken)

        val unknown = reader.findOpenByToken("6")!!
        assertEquals(ListedToken(listing(5, 10, 6, alice, 10), null, null, null), unknown)

        assertNull(reader.findOpenByToken("7"))
        assertNull(reader.findOpenByToken("99"))
    }

    @Test
    fun `rollback drops the block onward and reopens what it had closed`() {
        writer.rollbackFrom(20)

        assertEquals(listOf(listing(1, 10, 1, alice, 100)), writer.findCurrent(ids(1)))
        assertEquals(1L, saleId(reader.findOpenByToken("1")!!))
        // Sale 9 is open again, but token 7 has since gone to carol, so it stays invalid.
        assertEquals(listOf(2L, 1L), valid(ListingSort.NEWEST, Direction.DESC).map(::saleId))
        assertEquals(7, database.count(StargateListingRowMapping.TABLE))

        reseed()
    }

    @Test
    fun `prune drops rows superseded before the horizon and nothing current`() {
        assertEquals(1, writer.prune(25))

        assertEquals(9, database.count(StargateListingRowMapping.TABLE))
        assertEquals(listOf(8L, 2L), valid(ListingSort.NEWEST, Direction.DESC).map(::saleId))
        assertEquals(0, writer.prune(25))

        reseed()
    }

    @Test
    fun `truncate empties the table`() {
        writer.truncate()

        assertEquals(0, database.count(StargateListingRowMapping.TABLE))
        assertNull(reader.findOpenByToken("1"))

        seedListings()
    }

    @Test
    fun `the reads still answer with the deferrable index dropped`() {
        val builder = IndexBuilder(database.properties)
        builder.drop(StargateMarketplaceIndexes.SET)
        try {
            assertEquals(listOf(8L, 2L), valid(ListingSort.NEWEST, Direction.DESC).map(::saleId))
            assertEquals(8L, saleId(reader.findOpenByToken("1")!!))
        } finally {
            builder.build(StargateMarketplaceIndexes.SET)
        }
    }

    private fun valid(
        sort: ListingSort,
        direction: Direction,
        level: TokenLevel? = null,
        seller: String? = null,
        offset: Long = 0,
        limit: Int = 10,
    ) = reader.findValid(level, seller, sort, direction, offset, limit)

    private fun saleId(row: ListedToken) = row.listing.saleId.toLong()

    private fun ids(vararg ids: Long) = ids.map(BigInteger::valueOf)

    private fun token(
        tokenId: String,
        block: Long,
        owner: String,
        level: TokenLevel = TokenLevel.Dawn,
    ) =
        StargateToken(
            tokenId = tokenId,
            level = level,
            owner = owner,
            totalRewardsClaimed = BigInteger.ZERO,
            totalBootstrapRewardsClaimed = BigInteger.ZERO,
            vetStaked = BigInteger.TEN,
            migrated = false,
            boosted = false,
            blockNumber = block,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockTimestamp = block * 10,
        )

    private fun listing(
        saleId: Long,
        block: Long,
        tokenId: Long,
        seller: String,
        price: Long,
        status: ListingStatus = ListingStatus.OPEN,
        buyer: String? = null,
        token: String = vet,
    ) =
        StargateListing(
            saleId = BigInteger.valueOf(saleId),
            blockNumber = block,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockTimestamp = block * 10,
            txId = "0x" + "ff".repeat(32),
            tokenId = BigInteger.valueOf(tokenId),
            seller = seller,
            price = BigInteger.valueOf(price),
            paymentToken = token,
            status = status,
            buyer = buyer,
        )
}
