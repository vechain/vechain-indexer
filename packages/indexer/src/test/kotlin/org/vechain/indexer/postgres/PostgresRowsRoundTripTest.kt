package org.vechain.indexer.postgres

import io.mockk.mockk
import java.math.BigInteger
import kotlin.reflect.full.memberProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.vechain.indexer.blocks.BlocksService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.fixtures.IndexedEventsFixtures
import org.vechain.indexer.postgres.PostgresRowMapping.BlockRef
import org.vechain.indexer.thor.DecodedEvent
import org.vechain.indexer.thor.DecodedOutputs
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.transaction.IndexedTransaction
import org.vechain.indexer.transaction.TransactionService

/** `assemble(flatten(tx)) == tx` over every block fixture, decoded the way the indexer does. */
class PostgresRowsRoundTripTest {

    private val blocks = BlocksService(mockk())
    private val transactions = TransactionService(mockk())

    private val decodedEvents =
        mapOf(
            "BLOCK_TRANSFERS" to IndexedEventsFixtures.INDEXED_EVENTS_TRANSFERS,
            "BLOCK_NFT_MINT_2" to IndexedEventsFixtures.INDEXED_EVENTS_NFT_MINT,
            "BLOCK_NFT_MINT_REVERTED" to IndexedEventsFixtures.INDEXED_EVENTS_NFT_MINT,
        )

    private fun fixtures(): List<Pair<String, Block>> =
        BlockFixtures::class
            .memberProperties
            .filter { it.returnType.classifier == Block::class }
            .map { it.name to it.get(BlockFixtures) as Block }

    @TestFactory
    fun `every fixture block survives flatten and assemble`(): List<DynamicTest> =
        fixtures().map { (name, block) ->
            dynamicTest(name) {
                val events: List<IndexedEvent> = decodedEvents[name].orEmpty()
                val indexedBlock = blocks.processBlock(block)
                val txs = transactions.processBlock(block, events)

                val blockRow = PostgresRowMapping.flattenBlock(indexedBlock, BlockTotals.ZERO)
                assertEquals(
                    indexedBlock,
                    PostgresRowMapping.assembleBlock(blockRow, txs.map { it.id }),
                )

                txs.forEach { tx -> assertEquals(normalise(tx), roundTrip(tx)) }
                assertTrue(txs.size == block.transactions.size)
            }
        }

    @Test
    fun `decoded events keep their names and parameters`() {
        val txs =
            transactions.processBlock(
                BlockFixtures.BLOCK_NFT_MINT_2,
                IndexedEventsFixtures.INDEXED_EVENTS_NFT_MINT,
            )
        val decoded = txs.flatMap { it.outputs }.flatMap { it.events }.filter { it.name != null }

        assertTrue(decoded.isNotEmpty(), "the fixture pair should decode at least one event")
        assertEquals(normalise(txs.first()), roundTrip(txs.first()))
    }

    @Test
    fun `big-number parameters come back as strings, like Mongo returned them`() {
        val base =
            transactions.processBlock(BlockFixtures.BLOCK_SINGLE_CLAUSE, emptyList()).single()
        val event =
            DecodedEvent(
                address = base.origin,
                topics = listOf("0x" + "ab".repeat(32)),
                data = "0x",
                name = "Transfer",
                params = mapOf("value" to BigInteger.TWO.pow(200), "count" to 7, "ok" to false),
            )
        val tx =
            base.copy(outputs = listOf(DecodedOutputs(base.origin, listOf(event), emptyList())))

        val params = roundTrip(tx).outputs.single().events.single().params!!

        assertEquals(BigInteger.TWO.pow(200).toString(), params["value"])
        assertEquals(7, params["count"])
        assertEquals(false, params["ok"])
    }

    private fun roundTrip(tx: IndexedTransaction): IndexedTransaction {
        val rows = PostgresRowMapping.flatten(tx)
        return PostgresRowMapping.assemble(
            rows.transaction,
            BlockRef(tx.blockId, tx.blockTimestamp),
            rows.clauses,
            rows.events.shuffled(),
            rows.transfers.shuffled(),
        )
    }

    /**
     * Params compare after the JSON pass Mongo's converters also applied (big numbers as strings).
     */
    private fun normalise(tx: IndexedTransaction): IndexedTransaction =
        tx.copy(
            outputs =
                tx.outputs.map { output ->
                    output.copy(
                        events =
                            output.events.map {
                                it.copy(params = PostgresJson.read(PostgresJson.write(it.params)))
                            }
                    )
                }
        )
}
