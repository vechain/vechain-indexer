package org.vechain.indexer.validator

import com.mongodb.bulk.BulkWriteResult
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.rest.ExecuteAccountResponse
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.logic.ValidatorAssembler
import org.vechain.indexer.validator.logic.ValidatorAssembler.getLatestValidatorInfo

@ExtendWith(MockKExtension::class)
class ValidatorServiceTest {
    private val repository = mockk<ValidatorRepository>()
    private val archiveService = mockk<ArchiveService<Validator, ValidatorArchive>>(relaxed = true)
    private val thorService = mockk<ThorService>()

    @MockK(relaxed = true) lateinit var mongoTemplate: MongoTemplate

    @MockK lateinit var pruner: TargetedPruner<Validator, ValidatorArchive>

    private lateinit var service: ValidatorService

    @BeforeEach
    fun setup() {
        clearAllMocks()
        service =
            spyk(
                ValidatorService(
                    repository,
                    archiveService,
                    thorService,
                    mongoTemplate,
                    pruner,
                    25L,
                    "0xcontract",
                )
            )
    }

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
        every { thorService.getBestBlock() } returns block(100)
        val oldBlock = block(50)

        val result = service.processBlock(oldBlock, emptyList(), emptyList())

        assertThat(result.first).isEmpty()
        assertThat(result.second).isEmpty()
    }

    @Test
    fun `apply beneficiary changes for old blocks`() {
        every { thorService.getBestBlock() } returns block(100)

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

        val result = service.processBlock(block(7), listOf(ev), emptyList())

        val updated = result.first.single()
        assertThat(updated.id).isEqualTo("0xVAL1")
        assertThat(updated.beneficiary).isEqualTo("0xBEN")
        assertThat(result.second).isEmpty()
    }

    @Test
    fun `recent blocks load ABIs and update chain state`() {
        every { thorService.getBestBlock() } returns block(200)
        every { repository.findByStatusNot(any()) } returns emptyList()

        // Fake ABI + responses
        val abi = AbiElement(name = "getValidators", type = "function")
        mockkObject(ValidatorAssembler)
        every {
            getLatestValidatorInfo(any(), any(), any(), any(), any(), any(), BigInteger.ZERO)
        } returns
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
        every { thorService.inspectBalanceAtBlock(any(), any()) } returns
            ExecuteAccountResponse(balance = "0x0", energy = "0x0", hasCode = false)

        // FIX: InspectionResult must get a List<TxEvent>, not a BigInteger
        val inspectionResult =
            InspectionResult(
                data = "0xDATA",
                events = emptyList(),
                transfers = emptyList(),
                reverted = false,
                vmError = "",
            )

        val result = service.processBlock(block(190), emptyList(), listOf(inspectionResult))

        val updated = result.first.single()
        assertThat(updated.id).isEqualTo("0xVAL1")
        verify { getLatestValidatorInfo(any(), any(), any(), any(), any(), any(), BigInteger.ZERO) }
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

        every { repository.deleteAllById(any<List<String>>()) } just Runs
        every { archiveService.saveAll(any<List<Validator>>()) } just Runs

        val mockBulkOps = mockk<BulkOperations>(relaxed = true)

        // BulkOps returned by mongoTemplate
        every { mongoTemplate.bulkOps(any(), Validator::class.java) } returns mockBulkOps

        // replaceOne for updated record
        every { mockBulkOps.replaceOne(any(), v1, any()) } returns mockBulkOps

        // Bulk result
        val mockResult = mockk<BulkWriteResult>()
        every { mockResult.modifiedCount } returns 1
        every { mockResult.insertedCount } returns 0
        every { mockResult.upserts } returns emptyList()

        every { mockBulkOps.execute() } returns mockResult

        service.save(listOf(v1), listOf(v1))

        // Verify bulkOps was used to persist updates
        verify(exactly = 1) { mongoTemplate.bulkOps(any(), Validator::class.java) }

        verify(exactly = 1) { mockBulkOps.replaceOne(any(), v1, any()) }

        // Verify bulk execution
        verify(exactly = 1) { mockBulkOps.execute() }
        verify { archiveService.saveAll(match { it.isNotEmpty() }) }
    }
}
