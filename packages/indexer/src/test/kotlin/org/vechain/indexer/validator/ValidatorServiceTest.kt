package org.vechain.indexer.validator

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.client.ExecuteAccountResponse
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.logic.ValidatorAssembler
import org.vechain.indexer.validator.logic.ValidatorAssembler.getLatestValidatorInfo

class ValidatorServiceTest {
    private val repository = mockk<ValidatorRepository>()
    private val archiveService = mockk<ArchiveService<Validator, ValidatorArchive>>(relaxed = true)
    private val thorClient = mockk<ThorClient>()

    private lateinit var service: ValidatorService

    @BeforeEach
    fun setup() {
        clearAllMocks()
        service = spyk(ValidatorService(repository, archiveService, thorClient, 25L, "0xcontract"))
    }

    private fun inspectionResult(data: String): InspectionResult =
        InspectionResult(
            vmError = null,
            data = data,
            reverted = false,
            events = emptyList(),
            transfers = emptyList(),
            gasUsed = 0,
        )

    private fun validBlockId(hexChar: Char): String = "0x" + hexChar.toString().repeat(64)

    private fun block(num: Long) =
        Block(
            id = "0xBLOCK",
            number = num,
            timestamp = 1234567890,
            parentID = "0xPARENT",
            size = 0,
            gasLimit = 0,
            baseFeePerGas = null,
            beneficiary = "0xBENEFICIARY",
            gasUsed = 0,
            totalScore = 0,
            txsRoot = "0xTXROOT",
            txsFeatures = 0,
            stateRoot = "0xSTATEROOT",
            receiptsRoot = "0xRECEIPTSROOT",
            signer = "0xSIGNER",
            isTrunk = true,
            isFinalized = true,
            transactions = emptyList(),
            com = false,
        )

    private fun makeEvent(
        blockNumber: Long,
        validator: String,
        beneficiary: String,
        type: String = "BeneficiaryChanged",
    ): IndexedEvent =
        IndexedEvent(
            id = "evt1",
            blockId = "0xBLOCK",
            blockNumber = blockNumber,
            blockTimestamp = 111,
            txId = "0xTX",
            origin = "0xORIGIN",
            paid = null,
            gasUsed = null,
            gasPayer = null,
            raw = null,
            params =
                AbiEventParameters(
                    returnValues = mapOf("validator" to validator, "beneficiary" to beneficiary)
                ),
            address = "0xcontract",
            eventType = type,
            clauseIndex = 0,
            signature = null,
        )

    // --- processBlock tests ---

    @Test
    fun `skip old irrelevant blocks when no events`() {
        val oldBlock = block(50)

        val result = service.processBlock(oldBlock, emptyList(), emptyList(), isFullySynced = false)

        assertThat(result.first).isEmpty()
        assertThat(result.second).isEmpty()
    }

    @Test
    fun `apply beneficiary changes for old blocks`() {
        val ev = makeEvent(7, "0xVAL1", "0xBEN")

        every { repository.findAllById(any<List<String>>()) } returns
            listOf(
                Validator(
                    id = "0xVAL1",
                    blockId = "oldBlock",
                    blockNumber = 5,
                    blockTimestamp = 123,
                    beneficiary = "0xOLD",
                    status = Status.ACTIVE,
                    version = 1,
                )
            )

        val result = service.processBlock(block(7), listOf(ev), emptyList(), isFullySynced = false)

        val updated = result.first.single()
        assertThat(updated.id).isEqualTo("0xVAL1")
        assertThat(updated.beneficiary).isEqualTo("0xBEN")
        assertThat(result.second).isEmpty()
    }

    @Test
    fun `recent blocks load ABIs and update chain state`() {
        every { repository.findByStatusNot(any()) } returns emptyList()

        // Fake ABI + responses
        val abi = AbiElement(name = "getValidators", type = "function")
        mockkObject(ValidatorAssembler)
        every { getLatestValidatorInfo(any(), any(), any(), any(), any(), any()) } returns
            listOf(
                Validator(
                    id = "0xVAL1",
                    blockId = "0xBLOCK",
                    blockNumber = 190,
                    blockTimestamp = 111,
                    beneficiary = "0xBEN",
                    version = 1,
                )
            )
        coEvery { thorClient.getAccountState(any(), any()) } returns
            ExecuteAccountResponse(balance = "0x0", energy = "0x0", hasCode = false)

        // FIX: InspectionResult must get a List<TxEvent>, not a BigInteger
        val inspectionResult =
            InspectionResult(
                data = "0xDATA",
                events = emptyList(),
                transfers = emptyList(),
                gasUsed = 0,
                reverted = false,
                vmError = "",
            )

        val result =
            service.processBlock(
                block(190),
                emptyList(),
                listOf(inspectionResult),
                isFullySynced = true,
            )

        val updated = result.first.single()
        assertThat(updated.id).isEqualTo("0xVAL1")
        verify { getLatestValidatorInfo(any(), any(), any(), any(), any(), any()) }
    }

    // --- saveAndDelete tests ---

    @Test
    fun `saveAndDelete persists updates, archives, and deletes`() {
        val v1 =
            Validator(
                id = "0xVAL1",
                blockId = "b",
                blockNumber = 1,
                blockTimestamp = 1,
                version = 1,
            )

        every { repository.saveAll(any<List<Validator>>()) } returns listOf(v1)
        every { repository.deleteAllById(any<List<String>>()) } just Runs
        every { archiveService.saveAll(any<List<Validator>>()) } just Runs

        service.save(listOf(v1), listOf(v1))

        verify {
            repository.saveAll(withArg<List<Validator>> { list -> assertThat(list).contains(v1) })
        }
        verify { archiveService.saveAll(match { it.isNotEmpty() }) }
    }

    // --- Queue Initialization Tests ---

    @Test
    fun `initializeQueuePositionsIfNeeded initializes only once`() {
        runBlocking {
            val zeroAddressData =
                "0x0000000000000000000000000000000000000000000000000000000000000000"
            coEvery { thorClient.inspectClauses(any(), any()) } returns
                listOf(inspectionResult(zeroAddressData))

            service.initializeQueuePositionsIfNeeded(validBlockId('1'))
            service.initializeQueuePositionsIfNeeded(validBlockId('2'))
            service.initializeQueuePositionsIfNeeded(validBlockId('3'))
        }

        coVerify(exactly = 1) { thorClient.inspectClauses(any(), any()) }
    }

    @Test
    fun `initializeQueuePositionsIfNeeded does nothing when queue is empty`() {
        runBlocking {
            val zeroAddressData =
                "0x0000000000000000000000000000000000000000000000000000000000000000"
            coEvery { thorClient.inspectClauses(any(), any()) } returns
                listOf(inspectionResult(zeroAddressData))

            service.initializeQueuePositionsIfNeeded(validBlockId('b'))
        }

        coVerify(exactly = 1) { thorClient.inspectClauses(any(), any()) }
        verify(exactly = 0) { repository.findAllById(any<List<String>>()) }
        verify(exactly = 0) { repository.saveAll(any<List<Validator>>()) }
    }

    @Test
    fun `initializeQueuePositionsIfNeeded fetches queue order and updates positions`() {
        // Encode addresses as ABI-encoded return values (32-byte padded)
        val val1 = "0x000000000000000000000000aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1"
        val val2 = "0x000000000000000000000000aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa2"
        val zeroAddress = "0x0000000000000000000000000000000000000000000000000000000000000000"

        runBlocking {
            // Mock the iteration: firstQueued -> val1 -> val2 -> zero (end)
            coEvery { thorClient.inspectClauses(any(), any()) } returnsMany
                listOf(
                    listOf(inspectionResult(val1)), // firstQueued returns val1
                    listOf(inspectionResult(val2)), // next(val1) returns val2
                    listOf(inspectionResult(zeroAddress)), // next(val2) returns zero (end)
                )

            val existingValidators =
                listOf(
                    Validator(
                        id = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1",
                        blockId = "old",
                        blockNumber = 100,
                        blockTimestamp = 123,
                        status = Status.QUEUED,
                        version = 1,
                    ),
                    Validator(
                        id = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa2",
                        blockId = "old",
                        blockNumber = 100,
                        blockTimestamp = 123,
                        status = Status.QUEUED,
                        version = 1,
                    ),
                )
            every { repository.findAllById(any<List<String>>()) } returns existingValidators

            val savedSlot = slot<List<Validator>>()
            every { repository.saveAll(capture(savedSlot)) } answers { savedSlot.captured }

            service.initializeQueuePositionsIfNeeded(validBlockId('b'))

            verify { repository.saveAll(any<List<Validator>>()) }
            val saved = savedSlot.captured
            assertThat(saved).hasSize(2)
            assertThat(
                    saved
                        .find { it.id == "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1" }
                        ?.queuePosition
                )
                .isEqualTo(1)
            assertThat(
                    saved
                        .find { it.id == "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa2" }
                        ?.queuePosition
                )
                .isEqualTo(2)
        }
    }

    @Test
    fun `initializeQueuePositionsIfNeeded skips validators not found in repository`() {
        val val1 = "0x000000000000000000000000aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1"
        val val2 = "0x000000000000000000000000aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa2"
        val zeroAddress = "0x0000000000000000000000000000000000000000000000000000000000000000"

        runBlocking {
            coEvery { thorClient.inspectClauses(any(), any()) } returnsMany
                listOf(
                    listOf(inspectionResult(val1)),
                    listOf(inspectionResult(val2)),
                    listOf(inspectionResult(zeroAddress)),
                )

            // Only val1 exists in DB, val2 is unknown
            val existingValidators =
                listOf(
                    Validator(
                        id = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1",
                        blockId = "old",
                        blockNumber = 100,
                        blockTimestamp = 123,
                        status = Status.QUEUED,
                        version = 1,
                    )
                )
            every { repository.findAllById(any<List<String>>()) } returns existingValidators

            val savedSlot = slot<List<Validator>>()
            every { repository.saveAll(capture(savedSlot)) } answers { savedSlot.captured }

            service.initializeQueuePositionsIfNeeded(validBlockId('b'))

            val saved = savedSlot.captured
            // Only val1 should be saved (val2 skipped as not in repository)
            assertThat(saved).hasSize(1)
            assertThat(saved[0].id).isEqualTo("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1")
            assertThat(saved[0].queuePosition).isEqualTo(1)
        }
    }

    @Test
    fun `initializeQueuePositionsIfNeeded preserves version when updating`() {
        val val1 = "0x000000000000000000000000aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1"
        val zeroAddress = "0x0000000000000000000000000000000000000000000000000000000000000000"

        runBlocking {
            coEvery { thorClient.inspectClauses(any(), any()) } returnsMany
                listOf(listOf(inspectionResult(val1)), listOf(inspectionResult(zeroAddress)))

            val existingValidator =
                Validator(
                    id = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1",
                    blockId = "old",
                    blockNumber = 100,
                    blockTimestamp = 123,
                    status = Status.QUEUED,
                    version = 5,
                )
            every { repository.findAllById(any<List<String>>()) } returns listOf(existingValidator)

            val savedSlot = slot<List<Validator>>()
            every { repository.saveAll(capture(savedSlot)) } answers { savedSlot.captured }

            service.initializeQueuePositionsIfNeeded(validBlockId('b'))

            val saved = savedSlot.captured
            assertThat(saved[0].version)
                .isEqualTo(5) // version unchanged during queue initialization
        }
    }

    @Test
    fun `initializeQueuePositionsIfNeeded handles empty response from thor`() {
        runBlocking {
            coEvery { thorClient.inspectClauses(any(), any()) } returns emptyList()

            service.initializeQueuePositionsIfNeeded(validBlockId('b'))
        }

        coVerify(exactly = 1) { thorClient.inspectClauses(any(), any()) }
        verify(exactly = 0) { repository.findAllById(any<List<String>>()) }
        verify(exactly = 0) { repository.saveAll(any<List<Validator>>()) }
    }
}
