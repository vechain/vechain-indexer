package org.vechain.indexer.wov.marketplace

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.postgres.IndexBuilder
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WovMarketplaceRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: WovMarketplaceWriteRepository
    private lateinit var reader: WovMarketplaceReadRepository

    private val alice = "0x" + "aa".repeat(20)
    private val bob = "0x" + "bb".repeat(20)
    private val carol = "0x" + "cc".repeat(20)
    private val vet = "0x" + "00".repeat(20)
    private val wov = "0x" + "11".repeat(20)
    private val vvet = "0x" + "22".repeat(20)
    private val market = "0x" + "33".repeat(20)

    @BeforeAll
    fun start() {
        database.start()
        writer = WovMarketplaceWriteRepository(database.jdbc)
        reader = WovMarketplaceReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    /**
     * Alice buys with VET at blocks 10, 20 and 40 and with WoV at 20; Bob with VET at 30; Carol
     * with VVET at 50. Block n has timestamp 10n; every entry ends on its block.
     */
    private fun seed() {
        writer.save(
            entry(
                10,
                terms = listOf(terms(1, vet, 100)),
                sales = listOf(sale("s1", 10, alice, vet, 100, WovMechanism.CUSTODIAL)),
                running = listOf(running(alice, vet, 10, 1, 100)),
                buyers = listOf(WovBuyer(alice, 10, 100)),
            )
        )
        writer.save(
            entry(
                20,
                sales =
                    listOf(
                        sale("s2", 20, alice, vet, 200, WovMechanism.NON_CUSTODIAL),
                        sale("s3", 20, alice, wov, 5, WovMechanism.OFFER),
                    ),
                running = listOf(running(alice, vet, 20, 3, 300), running(alice, wov, 20, 1, 5)),
            )
        )
        writer.save(
            entry(
                30,
                sales = listOf(sale("s4", 30, bob, vet, 50, WovMechanism.AUCTION)),
                running = listOf(running(bob, vet, 30, 2, 50)),
                buyers = listOf(WovBuyer(bob, 30, 300)),
            )
        )
        writer.save(
            entry(
                40,
                sales = listOf(sale("s5", 40, alice, vet, 150, WovMechanism.NON_CUSTODIAL)),
                running = listOf(running(alice, vet, 40, 4, 450)),
            )
        )
        writer.save(
            entry(
                50,
                sales = listOf(sale("s6", 50, carol, vvet, 7, WovMechanism.OFFER)),
                running = listOf(running(carol, vvet, 50, 1, 7)),
                buyers = listOf(WovBuyer(carol, 50, 500)),
            )
        )
    }

    private fun reseed() {
        writer.truncate()
        seed()
    }

    @Test
    fun `a sale and its terms round-trip, and the running seed is the row before the block`() {
        val key = WovTermsKey(market, BigInteger.ONE)

        assertEquals(listOf(terms(1, vet, 100)), writer.findTerms(listOf(key)))
        assertEquals(
            emptyList<WovSaleTerms>(),
            writer.findTerms(listOf(WovTermsKey(market, BigInteger.TWO))),
        )
        assertEquals(
            listOf(sale("s1", 10, alice, vet, 100, WovMechanism.CUSTODIAL)),
            database.jdbc.query(
                "SELECT * FROM ${WovSaleRowMapping.TABLE} WHERE block_number = 10",
                { rs, _ -> WovSaleRowMapping.read(rs) },
            ),
        )
        assertEquals(
            listOf(running(alice, vet, 20, 3, 300), running(bob, vet, 30, 2, 50)),
            writer.findRunningBefore(listOf(alice to vet, bob to vet, alice to vvet), 40),
        )
        assertEquals(
            emptyList<WovBuyerRunning>(),
            writer.findRunningBefore(listOf(alice to vet), 10),
        )
    }

    @Test
    fun `the lifetime window sums every token a buyer paid with`() {
        assertEquals(500L, reader.indexedThrough())
        assertEquals(
            listOf(
                WovBuyerWindow(alice, listOf(spend(vet, 4, 450), spend(wov, 1, 5))),
                WovBuyerWindow(bob, listOf(spend(vet, 2, 50))),
                WovBuyerWindow(carol, listOf(spend(vvet, 1, 7))),
            ),
            reader.buyers(0, 600, null, 10),
        )
        assertEquals(5L, reader.buyers(0, 600, null, 1).single().items)
    }

    @Test
    fun `a window ends before its upper bound and starts on its lower one`() {
        assertEquals(emptyList<WovBuyerWindow>(), reader.buyers(0, 100, null, 10))
        assertEquals(
            listOf(WovBuyerWindow(alice, listOf(spend(vet, 1, 100)))),
            reader.buyers(0, 101, null, 10),
        )
        assertEquals(
            listOf(WovBuyerWindow(alice, listOf(spend(vet, 1, 100)))),
            reader.buyers(100, 200, null, 10),
        )
    }

    @Test
    fun `a window inside a buyer's history is the difference of its two edges`() {
        assertEquals(
            listOf(
                WovBuyerWindow(alice, listOf(spend(vet, 2, 200), spend(wov, 1, 5))),
                WovBuyerWindow(bob, listOf(spend(vet, 2, 50))),
            ),
            reader.buyers(150, 350, null, 10),
        )
    }

    @Test
    fun `a buyer idle in the window is absent, however much they bought before it`() {
        assertEquals(
            listOf(WovBuyerWindow(bob, listOf(spend(vet, 2, 50)))),
            reader.buyers(250, 350, null, 10),
        )
    }

    @Test
    fun `a page is limited by buyers, not token rows, and resumes after its last address`() {
        val first = reader.buyers(0, 600, null, 2)

        assertEquals(listOf(alice, bob), first.map { it.buyer })
        assertEquals(listOf(carol), reader.buyers(0, 600, first.last().buyer, 2).map { it.buyer })
        assertEquals(emptyList<WovBuyerWindow>(), reader.buyers(0, 600, carol, 2))
    }

    @Test
    fun `rollback drops the sales, totals, progress and buyers the block onward added`() {
        writer.rollbackFrom(30)

        assertEquals(200L, reader.indexedThrough())
        assertEquals(listOf(alice), reader.buyers(0, 600, null, 10).map { it.buyer })
        assertEquals(3, database.count(WovSaleRowMapping.TABLE))
        assertEquals(1, database.count(WovBuyerRowMapping.TABLE))
        assertEquals(listOf(alice to 20L), latestRunning())

        reseed()
    }

    @Test
    fun `prune keeps the newest progress row, so indexed-through survives it`() {
        assertEquals(3, writer.prune(40))

        assertEquals(500L, reader.indexedThrough())
        assertEquals(2, database.count("wov_marketplace.progress"))
        assertEquals(0, writer.prune(40))

        reseed()
    }

    @Test
    fun `truncate empties every table, so there is nothing indexed through`() {
        writer.truncate()

        assertNull(reader.indexedThrough())
        assertEquals(0, database.count(WovBuyerRunningRowMapping.TABLE))
        assertEquals(0, database.count(WovTermsRowMapping.TABLE))

        seed()
    }

    @Test
    fun `the seed and the terms still read with the deferrable index dropped`() {
        val builder = IndexBuilder(database.properties)
        builder.drop(WovMarketplaceIndexes.SET)
        try {
            assertEquals(
                listOf(running(alice, vet, 40, 4, 450)),
                writer.findRunningBefore(listOf(alice to vet), 50),
            )
            assertEquals(1, writer.findTerms(listOf(WovTermsKey(market, BigInteger.ONE))).size)
        } finally {
            builder.build(WovMarketplaceIndexes.SET)
        }
    }

    private fun latestRunning() =
        database.jdbc.query(
            "SELECT buyer, max(block_number) FROM ${WovBuyerRunningRowMapping.TABLE} GROUP BY buyer"
        ) { rs, _ ->
            org.vechain.indexer.postgres.PostgresHex.hex(rs.getBytes(1)) to rs.getLong(2)
        }

    private fun entry(
        block: Long,
        sales: List<WovSale> = emptyList(),
        terms: List<WovSaleTerms> = emptyList(),
        running: List<WovBuyerRunning> = emptyList(),
        buyers: List<WovBuyer> = emptyList(),
    ) = WovMarketplaceEntry(block, block * 10, sales, terms, running, buyers)

    private fun terms(id: Long, token: String, price: Long) =
        WovSaleTerms(market, BigInteger.valueOf(id), 5, token, BigInteger.valueOf(price))

    private fun sale(
        id: String,
        block: Long,
        buyer: String,
        token: String,
        price: Long,
        mechanism: WovMechanism,
    ) =
        WovSale(
            id = id.toByteArray().joinToString("") { "%02x".format(it) }.padEnd(40, '0'),
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            txId = "0x" + "ff".repeat(32),
            contractAddress = market,
            mechanism = mechanism,
            marketId = BigInteger.valueOf(block),
            nft = "0x" + "44".repeat(20),
            tokenId = BigInteger.valueOf(block),
            buyer = buyer,
            paymentToken = token,
            price = BigInteger.valueOf(price),
        )

    private fun running(buyer: String, token: String, block: Long, items: Long, spend: Long) =
        WovBuyerRunning(buyer, token, block, block * 10, items, BigInteger.valueOf(spend))

    private fun spend(token: String, items: Long, spend: Long) =
        WovTokenSpend(token, items, BigInteger.valueOf(spend))
}
