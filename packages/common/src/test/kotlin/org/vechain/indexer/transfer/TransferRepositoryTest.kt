package org.vechain.indexer.transfer

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.postgres.IndexBuilder
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransferRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: TransferWriteRepository
    private lateinit var reader: TransferReadRepository

    private val a = "0x" + "a".repeat(40)
    private val b = "0x" + "b".repeat(40)
    private val c = "0x" + "c".repeat(40)
    private val token1 = "0x" + "11".repeat(20)
    private val token2 = "0x" + "22".repeat(20)

    // (block, index): 01 FT a→b, 02 VET b→a, 03 NFT a→c, 04 FT a→a, 05 SF c→b, 06 FT c→a.
    private val transfers =
        listOf(
            transfer("01", 10, 0, TransferEventType.FUNGIBLE_TOKEN, a, b, token1),
            transfer("02", 10, 1, TransferEventType.VET, b, a, null),
            transfer("03", 20, 0, TransferEventType.NFT, a, c, token2, tokenId = "7"),
            transfer("04", 20, 1, TransferEventType.FUNGIBLE_TOKEN, a, a, token1),
            transfer("05", 30, 0, TransferEventType.SEMI_FUNGIBLE_TOKEN, c, b, token2, "9"),
            transfer("06", 30, 1, TransferEventType.FUNGIBLE_TOKEN, c, a, token1),
        )
    private val interactions =
        listOf(interaction(a, token1, 10), interaction(b, token1, 10), interaction(a, token2, 20))

    @BeforeAll
    fun start() {
        database.start()
        writer = TransferWriteRepository(database.jdbc)
        reader = TransferReadRepository(database.jdbc)
        writer.save(transfers, interactions)
    }

    @AfterAll fun stop() = database.close()

    private fun transfer(
        id: String,
        block: Long,
        index: Long,
        type: TransferEventType,
        from: String,
        to: String,
        token: String?,
        tokenId: String? = null,
    ) =
        IndexedTransferEvent(
            id = id.repeat(20),
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            transferIndex = index,
            txId = "0x" + block.toString(16).padStart(64, 'e'),
            from = from,
            to = to,
            value = "1000000000000000000",
            tokenAddress = token,
            tokenId = tokenId,
            topics =
                if (type == TransferEventType.VET) emptyList() else listOf("0x" + "ab".repeat(32)),
            eventType = type,
        )

    private fun interaction(wallet: String, contract: String, block: Long) =
        FungibleTokenInteraction(
            contractAddress = contract,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            walletAddress = wallet,
        )

    private fun ids(rows: List<IndexedTransferEvent>) = rows.map { it.id.take(2) }

    private fun find(
        to: String? = null,
        from: String? = null,
        toOrFrom: String? = null,
        token: String? = null,
        types: List<TransferEventType>? = null,
        after: Long? = null,
        before: Long? = null,
        offset: Long = 0,
        limit: Int = 10,
        direction: Direction = Direction.DESC,
    ) = ids(reader.find(to, from, toOrFrom, token, types, after, before, offset, limit, direction))

    @Test
    fun `a transfer round-trips, VET without a token and an NFT with its id`() {
        val byId = reader.find(null, null, a, null, null, null, null, 0, 10, Direction.DESC)
        assertEquals(transfers[1], byId.single { it.id.startsWith("02") })
        assertEquals(transfers[2], byId.single { it.id.startsWith("03") })
    }

    @Test
    fun `an address is found on either side once, paged by time in block order`() {
        assertEquals(listOf("06", "04", "03", "02", "01"), find(toOrFrom = a))
        assertEquals(
            listOf("01", "02", "03", "04", "06"),
            find(toOrFrom = a, direction = Direction.ASC),
        )
        assertEquals(listOf("04", "03"), find(toOrFrom = a, offset = 1, limit = 2))
        assertEquals(listOf("06", "04", "02"), find(to = a))
        assertEquals(listOf("04", "03", "01"), find(from = a))
    }

    @Test
    fun `the token, type and time filters narrow the page`() {
        assertEquals(listOf("06", "04", "01"), find(toOrFrom = a, token = token1))
        assertEquals(listOf("05", "03"), find(token = token2))
        val types = listOf(TransferEventType.VET, TransferEventType.NFT)
        assertEquals(listOf("03", "02"), find(toOrFrom = a, types = types))
        assertEquals(listOf("04", "03"), find(toOrFrom = a, after = 200, before = 200))
        assertEquals(listOf("01"), find(from = a, before = 150))
    }

    @Test
    fun `a block's transfers touching any of the addresses come in block order`() {
        assertEquals(
            listOf("03"),
            ids(reader.findByBlockNumber(20, listOf(c), 0, 10, Direction.ASC)),
        )
        assertEquals(
            listOf("04", "03"),
            ids(reader.findByBlockNumber(20, listOf(a, b), 0, 10, Direction.DESC)),
        )
    }

    @Test
    fun `the latest page walks newest block first and resumes after the cursor`() {
        val all = TransferEventType.entries
        assertEquals(listOf("05", "06", "03"), ids(reader.findLatest(all, null, 3)))
        assertEquals(
            listOf("04", "01", "02"),
            ids(reader.findLatest(all, LatestTransferCursor(20, 0), 10)),
        )
        val two = listOf(TransferEventType.FUNGIBLE_TOKEN, TransferEventType.NFT)
        assertEquals(listOf("06", "03", "04", "01"), ids(reader.findLatest(two, null, 10)))
        assertEquals(listOf("01"), ids(reader.findLatest(two, LatestTransferCursor(20, 1), 10)))
        assertEquals(listOf("02"), ids(reader.findLatest(listOf(TransferEventType.VET), null, 10)))
        assertEquals(
            listOf("03", "05"),
            ids(reader.findAllByEventType(TransferEventType.NFT)) +
                ids(reader.findAllByEventType(TransferEventType.SEMI_FUNGIBLE_TOKEN)),
        )
    }

    @Test
    fun `a wallet's contracts come newest first, optionally among the given ones`() {
        assertEquals(
            listOf(token2, token1),
            reader.findInteractedContracts(a, null, 0, 10, Direction.DESC),
        )
        assertEquals(
            listOf(token1, token2),
            reader.findInteractedContracts(a, null, 0, 10, Direction.ASC),
        )
        assertEquals(
            listOf(token1),
            reader.findInteractedContracts(a, listOf(token1), 0, 10, Direction.DESC),
        )
        assertEquals(
            emptyList<String>(),
            reader.findInteractedContracts(a, emptyList(), 0, 10, Direction.DESC),
        )
        assertEquals(
            emptyList<String>(),
            reader.findInteractedContracts(c, null, 0, 10, Direction.DESC),
        )
    }

    @Test
    fun `a replayed block changes nothing and a later touch keeps the first`() {
        writer.save(transfers, listOf(interaction(a, token1, 30)))
        assertEquals(6, database.count(TransferRowMapping.TABLE))
        assertEquals(3, database.count(TransferRowMapping.INTERACTION_TABLE))
        assertEquals(
            listOf(token2, token1),
            reader.findInteractedContracts(a, null, 0, 10, Direction.DESC),
        )
    }

    @Test
    fun `the write path holds up with the deferrable indexes dropped`() {
        val builder = IndexBuilder(database.properties)
        val repeat = transfer("07", 40, 0, TransferEventType.FUNGIBLE_TOKEN, a, b, token1)
        builder.drop(TransferIndexes.SET)
        try {
            // No key is left to absorb the repeat: the batch's own distinctBy keeps it to one.
            writer.save(listOf(repeat, repeat.copy(to = c)), listOf(interaction(a, token1, 40)))

            assertEquals(7, database.count(TransferRowMapping.TABLE))
            assertEquals(3, database.count(TransferRowMapping.INTERACTION_TABLE))

            writer.rollbackFrom(40)
            assertEquals(6, database.count(TransferRowMapping.TABLE))
        } finally {
            builder.build(TransferIndexes.SET)
        }
    }

    @Test
    fun `rollback drops both tables' rows from the block on and truncate empties them`() {
        writer.rollbackFrom(20)
        assertEquals(listOf("02", "01"), find(toOrFrom = a))
        assertEquals(listOf(token1), reader.findInteractedContracts(a, null, 0, 10, Direction.DESC))

        writer.truncate()
        assertEquals(0, database.count(TransferRowMapping.TABLE))
        assertEquals(0, database.count(TransferRowMapping.INTERACTION_TABLE))
        writer.save(transfers, interactions)
    }
}
