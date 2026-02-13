package org.vechain.indexer.contracts

import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.contracts.repository.ContractRepository
import org.vechain.indexer.contracts.specifications.Contracts
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.thor.client.AccountCodeResponse
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.ContractUtils

@ExtendWith(MockKExtension::class)
internal class ContractServiceTest {
    @MockK lateinit var repository: ContractRepository

    @MockK lateinit var archiveService: ArchiveService<Contract>

    @MockK lateinit var pruner: TargetedPruner<Contract>

    @MockK lateinit var thorClient: ThorClient

    private lateinit var service: TestableService

    private val blockIdA: String = "0x" + "a".repeat(64)
    private val blockIdB: String = "0x" + "b".repeat(64)

    private class TestableService(
        repository: ContractRepository,
        archiveService: ArchiveService<Contract>,
        pruner: TargetedPruner<Contract>,
        thorClient: ThorClient,
    ) : ContractService(repository, archiveService, pruner, thorClient) {
        suspend fun callCreateOrUpdateExisting(
            blockDetails: BlockDetails,
            contractAddress: String,
            events: List<IndexedEvent>,
            existing: Contract?,
            version: Int,
        ) = createOrUpdateExisting(blockDetails, contractAddress, events, existing, version)

        suspend fun callCreateNewRecord(
            blockDetails: BlockDetails,
            contractAddress: String,
            events: List<IndexedEvent>,
            version: Int,
        ) = createNewRecord(blockDetails, contractAddress, events, version)

        fun callUpdateExistingRecord(
            blockDetails: BlockDetails,
            events: List<IndexedEvent>,
            existing: Contract,
            version: Int,
        ) = updateExistingRecord(blockDetails, events, existing, version)
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = TestableService(repository, archiveService, pruner, thorClient)
    }

    private fun blockDetails(
        blockId: String = blockIdA,
        blockNumber: Long = 123L,
        blockTimestamp: Long = 999L,
    ) = BlockDetails(blockId = blockId, blockNumber = blockNumber, blockTimestamp = blockTimestamp)

    private fun masterEvent(
        newMaster: String,
        contractAddress: String = "0xCONTRACT",
    ): IndexedEvent =
        buildIndexedEvent(
            eventType = "\$Master",
            address = contractAddress,
            params = AbiEventParameters(returnValues = mapOf("newMaster" to newMaster)),
            blockId = blockIdA,
            blockNumber = 123L,
            blockTimestamp = 999L,
        )

    private fun existingContract(
        address: String = "0xCONTRACT",
        blockId: String = blockIdB,
        blockNumber: Long = 100L,
        blockTimestamp: Long = 1000L,
        version: Int = 5,
        createdOn: Long = 900L,
        deploymentTxId: String = "0xDEPLOY_TX",
        deploymentClauseIndex: Long = 0L,
        master: String = "0xMASTER_OLD",
    ) =
        Contract(
            address = address,
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            version = version,
            createdOn = createdOn,
            deploymentTxId = deploymentTxId,
            deploymentClauseIndex = deploymentClauseIndex,
            master = master,
            isErc20 = null,
            isErc721 = null,
            isErc1155 = null,
        )

    @Test
    fun `updateExistingRecord bumps version and updates master and block fields`() {
        val existing = existingContract()
        val details = blockDetails(blockId = blockIdB, blockNumber = 124L, blockTimestamp = 1111L)
        val events =
            listOf(
                masterEvent(newMaster = "0xMASTER_IGNORED"),
                masterEvent(newMaster = "0xMASTER_NEW"),
            )

        val updated =
            service.callUpdateExistingRecord(
                details,
                events,
                existing,
                version = existing.version + 1,
            )

        assertEquals(existing.address, updated.address)
        assertEquals(existing.createdOn, updated.createdOn)
        assertEquals(existing.deploymentTxId, updated.deploymentTxId)
        assertEquals(existing.deploymentClauseIndex, updated.deploymentClauseIndex)
        assertEquals(existing.version + 1, updated.version)
        assertEquals(details.blockId, updated.blockId)
        assertEquals(details.blockNumber, updated.blockNumber)
        assertEquals(details.blockTimestamp, updated.blockTimestamp)
        assertEquals("0xMASTER_NEW", updated.master)
    }

    @Test
    fun `createNewRecord returns null if account code is empty`() = runBlocking {
        mockkObject(ContractUtils)
        try {
            coEvery { thorClient.getAccountCode(any(), any()) } returns
                io.mockk.mockk<AccountCodeResponse>(relaxed = true) { every { code } returns "0x" }

            val details = blockDetails()
            val result =
                service.callCreateNewRecord(
                    details,
                    contractAddress = "0xCONTRACT",
                    events = listOf(masterEvent(newMaster = "0xDEPLOYER")),
                    version = 0,
                )

            assertNull(result)
            coVerify(exactly = 1) { thorClient.getAccountCode("0xCONTRACT", any()) }
            verify(exactly = 0) { ContractUtils.isContractType(any(), any()) }
        } finally {
            unmockkObject(ContractUtils)
        }
    }

    @Test
    fun `createNewRecord uses first event for deployment info and last event for master`() =
        runBlocking {
            mockkObject(ContractUtils)
            try {
                coEvery { thorClient.getAccountCode(any(), any()) } returns
                    io.mockk.mockk<AccountCodeResponse>(relaxed = true) {
                        every { code } returns "0xdeadbeef"
                    }

                every { ContractUtils.isContractType(Contracts.ERC20, any()) } returns true
                every { ContractUtils.isContractType(Contracts.ERC721, any()) } returns false
                every { ContractUtils.isContractType(Contracts.ERC1155, any()) } returns false

                val details =
                    blockDetails(blockId = blockIdA, blockNumber = 7L, blockTimestamp = 1700L)
                val result =
                    service.callCreateNewRecord(
                        details,
                        contractAddress = "0xCONTRACT",
                        events =
                            listOf(
                                masterEvent(newMaster = "0xDEPLOYER"),
                                masterEvent(newMaster = "0xMASTER_FINAL"),
                            ),
                        version = 0,
                    )!!

                assertEquals("0xCONTRACT", result.address)
                assertEquals(details.blockId, result.blockId)
                assertEquals(details.blockNumber, result.blockNumber)
                assertEquals(details.blockTimestamp, result.blockTimestamp)
                assertEquals(0, result.version)
                assertEquals(details.blockTimestamp, result.createdOn)
                assertEquals("tx-id", result.deploymentTxId)
                assertEquals(0L, result.deploymentClauseIndex)
                assertEquals("0xMASTER_FINAL", result.master)
                assertEquals(true, result.isErc20)
                assertEquals(false, result.isErc721)
                assertEquals(false, result.isErc1155)
            } finally {
                unmockkObject(ContractUtils)
            }
        }

    @Test
    fun `createOrUpdateExisting creates when existing is null`() = runBlocking {
        mockkObject(ContractUtils)
        try {
            coEvery { thorClient.getAccountCode(any(), any()) } returns
                io.mockk.mockk<AccountCodeResponse>(relaxed = true) {
                    every { code } returns "0xdeadbeef"
                }
            every { ContractUtils.isContractType(any(), any()) } returns false

            val details = blockDetails(blockTimestamp = 1700L)
            val result =
                service.callCreateOrUpdateExisting(
                    details,
                    contractAddress = "0xCONTRACT",
                    events = listOf(masterEvent(newMaster = "0xDEPLOYER")),
                    existing = null,
                    version = 0,
                )

            assertNotNull(result)
            assertEquals(0, result!!.version)
            assertEquals(details.blockTimestamp, result.createdOn)
        } finally {
            unmockkObject(ContractUtils)
        }
    }

    @Test
    fun `createOrUpdateExisting updates when existing is present`() = runBlocking {
        val existing = existingContract(version = 2, master = "0xOLD")
        val details = blockDetails(blockId = blockIdB, blockNumber = 2L, blockTimestamp = 3L)

        val result =
            service.callCreateOrUpdateExisting(
                details,
                contractAddress = "0xCONTRACT",
                events = listOf(masterEvent(newMaster = "0xNEW_MASTER")),
                existing = existing,
                version = existing.version + 1,
            )!!

        assertEquals(3, result.version)
        assertEquals("0xNEW_MASTER", result.master)
        assertEquals(details.blockId, result.blockId)
        assertEquals(details.blockNumber, result.blockNumber)
        assertEquals(details.blockTimestamp, result.blockTimestamp)

        coVerify(exactly = 0) { thorClient.getAccountCode(any(), any()) }
    }
}
