package org.vechain.indexer.contracts

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.client.AccountCodeResponse
import org.vechain.indexer.thor.client.ThorClient

@ExtendWith(MockKExtension::class)
internal class ContractServiceTest {
    @MockK lateinit var repository: ContractWriteRepository

    @MockK lateinit var thorClient: ThorClient

    private lateinit var service: ContractService

    private val contract = "0x" + "c".repeat(40)
    private val deployer = "0x" + "d".repeat(40)
    private val newMaster = "0x" + "e".repeat(40)

    @BeforeEach
    fun setUp() {
        service = ContractService(repository, thorClient)
        every { repository.findCurrentByAddresses(any()) } returns emptyList()
        coEvery { thorClient.getAccountCode(any(), any()) } returns
            mockk<AccountCodeResponse>(relaxed = true) {
                every { code } returns "0x" + "6080".repeat(4)
            }
    }

    private fun masterEvent(
        master: String,
        blockNumber: Long = 123L,
        blockTimestamp: Long = 999L,
    ): IndexedEvent =
        buildIndexedEvent(
            eventType = "\$Master",
            address = contract,
            params = AbiEventParameters(returnValues = mapOf("newMaster" to master)),
            blockId = "0x" + blockNumber.toString(16).padStart(64, '0'),
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
        )

    private fun existing(master: String) =
        Contract(
            address = contract,
            blockId = "0x" + "b".repeat(64),
            blockNumber = 100L,
            blockTimestamp = 1000L,
            createdOn = 900L,
            deploymentTxId = "0x" + "f".repeat(64),
            deploymentClauseIndex = 0L,
            master = master,
        )

    @Test
    fun `an unknown contract takes its deployment from the first event and its master from the last`() =
        runBlocking {
            val rows = service.processBlock(listOf(masterEvent(deployer), masterEvent(newMaster)))

            assertEquals(1, rows.size)
            val created = rows.single()
            assertEquals(contract, created.address)
            assertEquals(999L, created.createdOn)
            assertEquals("tx-id", created.deploymentTxId)
            assertEquals(0L, created.deploymentClauseIndex)
            assertEquals(newMaster, created.master)
        }

    @Test
    fun `a contract with no code on chain is not recorded`() = runBlocking {
        coEvery { thorClient.getAccountCode(any(), any()) } returns
            mockk<AccountCodeResponse>(relaxed = true) { every { code } returns "0x" }

        assertTrue(service.processBlock(listOf(masterEvent(deployer))).isEmpty())
    }

    @Test
    fun `a known contract keeps its deployment and only moves its master`() = runBlocking {
        every { repository.findCurrentByAddresses(setOf(contract)) } returns
            listOf(existing(deployer))

        val updated = service.processBlock(listOf(masterEvent(newMaster))).single()

        assertEquals(newMaster, updated.master)
        assertEquals(900L, updated.createdOn)
        assertEquals("0x" + "f".repeat(64), updated.deploymentTxId)
        assertEquals(123L, updated.blockNumber)
        coVerify(exactly = 0) { thorClient.getAccountCode(any(), any()) }
    }

    @Test
    fun `a contract created and reassigned across blocks yields a row per block`() = runBlocking {
        val rows =
            service.processBlock(
                listOf(
                    masterEvent(newMaster, blockNumber = 200L, blockTimestamp = 2000L),
                    masterEvent(deployer, blockNumber = 100L, blockTimestamp = 1000L),
                )
            )

        assertEquals(listOf(100L, 200L), rows.map { it.blockNumber })
        assertEquals(listOf(deployer, newMaster), rows.map { it.master })
        assertEquals(listOf(1000L, 1000L), rows.map { it.createdOn })
        coVerify(exactly = 1) { thorClient.getAccountCode(any(), any()) }
    }
}
