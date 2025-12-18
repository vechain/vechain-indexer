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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.repository.findByIdOrNull
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

    @MockK lateinit var archiveService: ArchiveService<Contract, ContractArchive>

    @MockK lateinit var pruner: TargetedPruner<Contract, ContractArchive>

    @MockK lateinit var thorClient: ThorClient

    private lateinit var service: TestableService

    private val blockIdA: String = "0x" + "a".repeat(64)
    private val blockIdB: String = "0x" + "b".repeat(64)

    private class TestableService(
        repository: ContractRepository,
        archiveService: ArchiveService<Contract, ContractArchive>,
        pruner: TargetedPruner<Contract, ContractArchive>,
        thorClient: ThorClient,
    ) : ContractService(repository, archiveService, pruner, thorClient) {
        suspend fun callCreateOrUpdateExisting(
            blockDetails: BlockDetails,
            contractAddress: String,
            events: List<IndexedEvent>,
            existing: Contract?,
        ) = createOrUpdateExisting(blockDetails, contractAddress, events, existing)

        suspend fun callCreateNewRecord(
            blockDetails: BlockDetails,
            contractAddress: String,
            events: List<IndexedEvent>,
        ) = createNewRecord(blockDetails, contractAddress, events)

        fun callUpdateExistingRecord(
            blockDetails: BlockDetails,
            events: List<IndexedEvent>,
            existing: Contract,
        ) = updateExistingRecord(blockDetails, events, existing)

        fun callResolveExisting(recordId: String, cache: Map<String, Contract>) =
            resolveExisting(recordId, cache)
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
        deployer: String = "0xDEPLOYER",
        master: String = "0xMASTER_OLD",
    ) =
        Contract(
            address = address,
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            version = version,
            createdOn = createdOn,
            deployer = deployer,
            master = master,
            isErc20 = null,
            isErc721 = null,
            isErc1155 = null,
        )

    @Test
    fun `resolveExisting returns from cache without hitting DB`() {
        val recordId = "id-1"
        val cached = existingContract(address = "0xCACHED")

        val resolved = service.callResolveExisting(recordId, mapOf(recordId to cached))

        assertSame(cached, resolved)
        verify(exactly = 0) { repository.findByIdOrNull(recordId) }
    }

    @Test
    fun `resolveExisting falls back to repository when cache miss`() {
        val recordId = "id-1"
        val fromDb = existingContract(address = "0xDB")
        every { repository.findByIdOrNull(recordId) } returns fromDb

        val resolved = service.callResolveExisting(recordId, emptyMap())

        assertSame(fromDb, resolved)
        verify(exactly = 1) { repository.findByIdOrNull(recordId) }
    }

    @Test
    fun `updateExistingRecord bumps version and updates master and block fields`() {
        val existing = existingContract()
        val details = blockDetails(blockId = blockIdB, blockNumber = 124L, blockTimestamp = 1111L)
        val events =
            listOf(
                masterEvent(newMaster = "0xMASTER_NEW"),
                masterEvent(newMaster = "0xMASTER_IGNORED"),
            )

        val updated = service.callUpdateExistingRecord(details, events, existing)

        assertEquals(existing.address, updated.address)
        assertEquals(existing.createdOn, updated.createdOn)
        assertEquals(existing.deployer, updated.deployer)
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
                )

            assertNull(result)
            coVerify(exactly = 1) { thorClient.getAccountCode("0xCONTRACT", any()) }
            verify(exactly = 0) { ContractUtils.isContractType(any(), any()) }
        } finally {
            unmockkObject(ContractUtils)
        }
    }

    @Test
    fun `createNewRecord uses first event as deployer and last event as master`() = runBlocking {
        mockkObject(ContractUtils)
        try {
            coEvery { thorClient.getAccountCode(any(), any()) } returns
                io.mockk.mockk<AccountCodeResponse>(relaxed = true) {
                    every { code } returns "0xdeadbeef"
                }

            every { ContractUtils.isContractType(Contracts.ERC20, any()) } returns true
            every { ContractUtils.isContractType(Contracts.ERC721, any()) } returns false
            every { ContractUtils.isContractType(Contracts.ERC1155, any()) } returns false

            val details = blockDetails(blockId = blockIdA, blockNumber = 7L, blockTimestamp = 1700L)
            val result =
                service.callCreateNewRecord(
                    details,
                    contractAddress = "0xCONTRACT",
                    events =
                        listOf(
                            masterEvent(newMaster = "0xDEPLOYER"),
                            masterEvent(newMaster = "0xMASTER_FINAL"),
                        ),
                )!!

            assertEquals("0xCONTRACT", result.address)
            assertEquals(details.blockId, result.blockId)
            assertEquals(details.blockNumber, result.blockNumber)
            assertEquals(details.blockTimestamp, result.blockTimestamp)
            assertEquals(0, result.version)
            assertEquals(details.blockTimestamp, result.createdOn)
            assertEquals("0xDEPLOYER", result.deployer)
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
                )

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
            )!!

        assertEquals(3, result.version)
        assertEquals("0xNEW_MASTER", result.master)
        assertEquals(details.blockId, result.blockId)
        assertEquals(details.blockNumber, result.blockNumber)
        assertEquals(details.blockTimestamp, result.blockTimestamp)

        coVerify(exactly = 0) { thorClient.getAccountCode(any(), any()) }
    }
}
