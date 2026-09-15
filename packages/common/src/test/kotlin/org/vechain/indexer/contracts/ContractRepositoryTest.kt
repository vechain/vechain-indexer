package org.vechain.indexer.contracts

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContractRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: ContractWriteRepository
    private lateinit var reader: ContractReadRepository

    private val alice = "0x" + "a".repeat(40)
    private val bob = "0x" + "b".repeat(40)
    private val token = "0x" + "11".repeat(20)
    private val market = "0x" + "22".repeat(20)

    @BeforeAll
    fun start() {
        database.start()
        writer = ContractWriteRepository(database.jdbc)
        reader = ContractReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    /** Alice deploys both contracts, then hands the token over to Bob at block 30. */
    private fun seed() {
        writer.save(
            listOf(
                contract(token, 10, alice, erc20 = true),
                contract(market, 20, alice),
                contract(token, 30, bob, erc20 = true).copy(createdOn = 100),
            )
        )
    }

    private fun contract(address: String, block: Long, master: String, erc20: Boolean? = null) =
        Contract(
            address = address,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            createdOn = block * 10,
            deploymentTxId = "0x" + block.toString(16).padStart(64, 'f'),
            deploymentClauseIndex = 1,
            master = master,
            isErc20 = erc20,
        )

    @Test
    fun `a contract round-trips and only its newest row is current`() {
        assertEquals(bob, reader.findByAddress(token)?.master)
        assertEquals(true, reader.findByAddress(token)?.isErc20)
        assertNull(reader.findByAddress(token)?.isErc721)
        assertEquals(100L, reader.findByAddress(token)?.createdOn)
        assertNull(reader.findByAddress("0x" + "9".repeat(40)))
        assertEquals(
            listOf(token to bob, market to alice),
            writer
                .findCurrentByAddresses(setOf(token, market))
                .sortedBy { it.address }
                .map { it.address to it.master },
        )
    }

    @Test
    fun `by-master pages the contracts an address still masters, oldest or newest first`() {
        assertEquals(
            listOf(market),
            reader.findByMaster(alice, 0, 10, Direction.DESC).map { it.address },
        )
        assertEquals(
            listOf(token),
            reader.findByMaster(bob, 0, 10, Direction.ASC).map { it.address },
        )
        assertEquals(emptyList<Contract>(), reader.findByMaster(alice, 1, 10, Direction.DESC))
    }

    @Test
    fun `rollback reopens the superseded row and truncate empties the table`() {
        writer.rollbackFrom(30)
        assertEquals(alice, reader.findByAddress(token)?.master)

        writer.truncate()
        assertNull(reader.findByAddress(token))
        seed()
    }

    @Test
    fun `prune drops superseded rows below the horizon and reports how many`() {
        assertEquals(0, writer.prune(30))
        assertEquals(1, writer.prune(31))
        assertEquals(bob, reader.findByAddress(token)?.master)
        writer.truncate()
        seed()
    }
}
