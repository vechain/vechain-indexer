package org.vechain.indexer.validator

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.WriteModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.domain.ValidatorDecoder
import org.vechain.indexer.validator.logic.ValidatorAssembler
import org.vechain.indexer.validator.models.DecodedValidatorInfo

class ValidatorServiceTest {
    private val repository = mockk<ValidatorRepository>()
    private val delegationRepository = mockk<DelegationRepository>()
    private val mongoTemplate = mockk<MongoTemplate>(relaxed = true)
    private val inlineVersioningProperties = mockk<InlineVersioningProperties>()

    private lateinit var service: ValidatorService

    @BeforeEach
    fun setup() {
        every { inlineVersioningProperties.blockWindow } returns 10000L
        every { inlineVersioningProperties.maxVersions } returns 100
        every { inlineVersioningProperties.minVersions } returns 20
        every { delegationRepository.aggregateActiveDelegationsByValidatorAndLevel() } returns
            emptyList()
        service =
            ValidatorService(
                repository,
                delegationRepository,
                mongoTemplate,
                inlineVersioningProperties,
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
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

    private fun inspectionResult(data: String = "0xDATA"): InspectionResult =
        InspectionResult(
            vmError = null,
            data = data,
            reverted = false,
            events = emptyList(),
            transfers = emptyList(),
            gasUsed = 0,
        )

    private fun makeBeneficiaryEvent(
        blockNumber: Long,
        validator: String,
        beneficiary: String,
    ): IndexedEvent =
        IndexedEvent(
            id = "evt-beneficiary",
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
            eventType = "BeneficiarySet",
            clauseIndex = 0,
            signature = null,
        )

    private fun makeStakeEvent(
        blockNumber: Long,
        validator: String,
        type: String,
        params: Map<String, Any>,
    ): IndexedEvent =
        IndexedEvent(
            id = "evt-$type",
            blockId = "0xBLOCK",
            blockNumber = blockNumber,
            blockTimestamp = 111,
            txId = "0xTX",
            origin = "0xORIGIN",
            paid = null,
            gasUsed = null,
            gasPayer = null,
            raw = null,
            params = AbiEventParameters(returnValues = mapOf("validator" to validator) + params),
            address = "0xcontract",
            eventType = type,
            clauseIndex = 0,
            signature = null,
        )

    private fun decodedValidatorMap(): Map<String, Any?> =
        mapOf(
            "masters" to listOf("0xVAL1"),
            "endorsors" to listOf("0xEND1"),
            "statuses" to listOf(BigInteger.TWO),
            "onlines" to listOf(true),
            "offlineBlocks" to listOf(BigInteger.ZERO),
            "stakingPeriodLengths" to listOf(10),
            "startBlocks" to listOf(BigInteger.TEN),
            "exitBlocks" to listOf(BigInteger.valueOf(4294967295)),
            "completedPeriods" to listOf(BigInteger.valueOf(5)),
            "validatorLockedStakes" to listOf(BigInteger("1000000000000000000")),
            "validatorLockedWeights" to listOf(BigInteger.valueOf(100)),
            "delegatorsStake" to listOf(BigInteger.ZERO),
            "validatorQueuedStakes" to listOf(BigInteger.ZERO),
            "totalQueuedStakes" to listOf(BigInteger.ZERO),
            "totalExitingStakes" to listOf(BigInteger.ZERO),
            "totalNextPeriodWeights" to listOf(BigInteger.valueOf(100)),
            "nextPeriodDelegationStakes" to listOf(BigInteger.ZERO),
        )

    private fun decodedInfo(decodedValidators: Map<String, Any?> = decodedValidatorMap()) =
        DecodedValidatorInfo(
            decodedValidators = decodedValidators,
            totalWeight = BigInteger.ONE,
            vthoTotalSupply = BigInteger.ZERO,
            vetPriceUsd = BigInteger.ONE,
            vthoPriceUsd = BigInteger.ONE,
            vthoBurned = BigInteger.ZERO,
        )

    @Test
    fun `processBlock returns no updates before helper exists when there are no events`() {
        every { repository.findByStatusNot(Status.EXITED) } returns emptyList()

        val result = service.processBlock(block(50), emptyList(), emptyList())

        assertThat(result.first).isEmpty()
        assertThat(result.second).isEmpty()
    }

    @Test
    fun `processBlock event-only fallback archives only changed validators`() {
        val validatorId = "0xVAL1"
        val existingValidator =
            Validator(
                id = validatorId,
                blockId = "oldBlock",
                blockNumber = 49,
                blockTimestamp = 123,
                beneficiary = "0xOLD",
                exitingValidatorVetStaked = BigDecimal("5000000"),
                version = 2,
            )
        val untouchedValidator =
            Validator(
                id = "0xVAL2",
                blockId = "oldBlock-2",
                blockNumber = 49,
                blockTimestamp = 123,
                beneficiary = "0xUNCHANGED",
                exitingValidatorVetStaked = BigDecimal("7000000"),
                version = 4,
            )

        every { repository.findByStatusNot(Status.EXITED) } returns
            listOf(existingValidator, untouchedValidator)

        val result =
            service.processBlock(
                block(50),
                listOf(makeBeneficiaryEvent(50, validatorId, "0xNEW")),
                emptyList(),
            )

        assertThat(result.first.map { it.id }).containsExactly(validatorId)
        val updated = result.first.first()
        assertThat(result.second).containsExactly(existingValidator)
        assertThat(updated.beneficiary).isEqualTo("0xNEW")
        assertThat(updated.version).isEqualTo(3)
    }

    @Test
    fun `processBlock fails when validator call data is missing after validator state exists`() {
        every { repository.findByStatusNot(Status.EXITED) } returns
            listOf(
                Validator(
                    id = "0xVAL1",
                    blockId = "oldBlock",
                    blockNumber = 49,
                    blockTimestamp = 123,
                    status = Status.ACTIVE,
                    version = 1,
                )
            )

        val exception =
            assertThrows<IllegalStateException> {
                service.processBlock(block(50), emptyList(), emptyList())
            }

        assertThat(exception.message).contains("Missing or invalid validator call data")
    }

    @Test
    fun `processBlock passes full persisted docs into assembler and archives only changed validators`() {
        val validatorId = "0xVAL1"
        val existingValidator =
            Validator(
                id = validatorId,
                blockId = "oldBlock",
                blockNumber = 100,
                blockTimestamp = 123,
                beneficiary = "0xOLD",
                status = Status.ACTIVE,
                exitingValidatorVetStaked = BigDecimal.ZERO,
                version = 41,
            )
        val untouchedValidator =
            Validator(
                id = "0xVAL2",
                blockId = "oldBlock-2",
                blockNumber = 100,
                blockTimestamp = 123,
                beneficiary = "0xUNCHANGED",
                status = Status.ACTIVE,
                exitingValidatorVetStaked = BigDecimal.ZERO,
                version = 7,
            )
        val updatedValidator =
            existingValidator.copy(
                blockId = "0xBLOCK",
                blockNumber = 200,
                blockTimestamp = 111,
                beneficiary = "0xBEN",
                exitingValidatorVetStaked = BigDecimal("25000000"),
                version = 42,
            )

        every { repository.findByStatusNot(Status.EXITED) } returns
            listOf(existingValidator, untouchedValidator)

        mockkObject(ValidatorDecoder, ValidatorAssembler)
        every { ValidatorDecoder.decodeResponseInfo(any(), any()) } returns decodedInfo()
        every { delegationRepository.aggregateActiveDelegationsByValidatorAndLevel() } returns
            listOf(
                DelegationValidatorLevelAggregateResult(
                    validator = validatorId,
                    level = TokenLevel.Dawn.name,
                    nftCount = 2L,
                )
            )

        val persistedDocsSlot = slot<Map<String, Validator>>()
        val carriedDocsSlot = slot<Map<String, Validator>>()
        val currentLevelsSlot = slot<Map<String, Map<TokenLevel, Long>>>()
        every {
            ValidatorAssembler.unpackValidators(
                any(),
                capture(persistedDocsSlot),
                capture(carriedDocsSlot),
                capture(currentLevelsSlot),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns listOf(updatedValidator)

        val decreasedWei = BigInteger("25000000000000000000000000")
        val result =
            service.processBlock(
                block(200),
                listOf(
                    makeBeneficiaryEvent(200, validatorId, "0xBEN"),
                    makeStakeEvent(
                        200,
                        validatorId,
                        "StakeDecreased",
                        mapOf("removed" to decreasedWei),
                    ),
                ),
                listOf(inspectionResult()),
            )

        assertThat(result.first).containsExactly(updatedValidator)
        assertThat(result.second).containsExactly(existingValidator)
        assertThat(persistedDocsSlot.captured[validatorId]).isEqualTo(existingValidator)
        assertThat(persistedDocsSlot.captured[untouchedValidator.id]).isEqualTo(untouchedValidator)
        assertThat(carriedDocsSlot.captured[validatorId]!!.beneficiary).isEqualTo("0xBEN")
        assertThat(carriedDocsSlot.captured[validatorId]!!.exitingValidatorVetStaked)
            .isEqualByComparingTo(BigDecimal("25000000"))
        assertThat(currentLevelsSlot.captured[validatorId]).isEqualTo(mapOf(TokenLevel.Dawn to 2L))
    }

    @Test
    fun `StakeIncreased reduces same-block exiting validator stake before assembly`() {
        val validatorId = "0xVAL1"
        val existingValidator =
            Validator(
                id = validatorId,
                blockId = "oldBlock",
                blockNumber = 100,
                blockTimestamp = 123,
                status = Status.ACTIVE,
                exitingValidatorVetStaked = BigDecimal("25000000"),
                version = 5,
            )

        every { repository.findByStatusNot(Status.EXITED) } returns listOf(existingValidator)

        mockkObject(ValidatorDecoder, ValidatorAssembler)
        every { ValidatorDecoder.decodeResponseInfo(any(), any()) } returns decodedInfo()

        val carriedDocsSlot = slot<Map<String, Validator>>()
        every {
            ValidatorAssembler.unpackValidators(
                any(),
                any(),
                capture(carriedDocsSlot),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns emptyList()

        val addedWei = BigInteger("10000000000000000000000000")
        service.processBlock(
            block(200),
            listOf(makeStakeEvent(200, validatorId, "StakeIncreased", mapOf("added" to addedWei))),
            listOf(inspectionResult()),
        )

        assertThat(carriedDocsSlot.captured[validatorId]!!.exitingValidatorVetStaked)
            .isEqualByComparingTo(BigDecimal("15000000"))
    }

    @Test
    fun `ValidationWithdrawn floors exiting validator stake at zero before assembly`() {
        val validatorId = "0xVAL1"
        val existingValidator =
            Validator(
                id = validatorId,
                blockId = "oldBlock",
                blockNumber = 100,
                blockTimestamp = 123,
                status = Status.ACTIVE,
                exitingValidatorVetStaked = BigDecimal("5000000"),
                version = 5,
            )

        every { repository.findByStatusNot(Status.EXITED) } returns listOf(existingValidator)

        mockkObject(ValidatorDecoder, ValidatorAssembler)
        every { ValidatorDecoder.decodeResponseInfo(any(), any()) } returns decodedInfo()

        val carriedDocsSlot = slot<Map<String, Validator>>()
        every {
            ValidatorAssembler.unpackValidators(
                any(),
                any(),
                capture(carriedDocsSlot),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns emptyList()

        val withdrawnWei = BigInteger("25000000000000000000000000")
        service.processBlock(
            block(200),
            listOf(
                makeStakeEvent(
                    200,
                    validatorId,
                    "ValidationWithdrawn",
                    mapOf("stake" to withdrawnWei),
                )
            ),
            listOf(inspectionResult()),
        )

        assertThat(carriedDocsSlot.captured[validatorId]!!.exitingValidatorVetStaked)
            .isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `new validator stake decrease is counted once before assembly`() {
        val validatorId = "0xNEW"

        every { repository.findByStatusNot(Status.EXITED) } returns emptyList()

        mockkObject(ValidatorDecoder, ValidatorAssembler)
        every { ValidatorDecoder.decodeResponseInfo(any(), any()) } returns decodedInfo()

        val carriedDocsSlot = slot<Map<String, Validator>>()
        every {
            ValidatorAssembler.unpackValidators(
                any(),
                any(),
                capture(carriedDocsSlot),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns emptyList()

        val decreasedWei = BigInteger("25000000000000000000000000")
        service.processBlock(
            block(200),
            listOf(
                makeStakeEvent(200, validatorId, "StakeDecreased", mapOf("removed" to decreasedWei))
            ),
            listOf(inspectionResult()),
        )

        assertThat(carriedDocsSlot.captured[validatorId]!!.version).isEqualTo(0)
        assertThat(carriedDocsSlot.captured[validatorId]!!.beneficiary).isNull()
        assertThat(carriedDocsSlot.captured[validatorId]!!.exitingValidatorVetStaked)
            .isEqualByComparingTo(BigDecimal("25000000"))
    }

    @Test
    fun `save persists updates with inline versioning`() {
        val validator =
            Validator(
                id = "0xVAL1",
                blockId = "b",
                blockNumber = 1,
                blockTimestamp = 1,
                version = 1,
            )

        val collection = mockk<MongoCollection<Document>>(relaxed = true)
        every { mongoTemplate.getCollectionName(Validator::class.java) } returns "validators"
        every { mongoTemplate.getCollection("validators") } returns collection

        service.save(listOf(validator), listOf(validator))

        verify(exactly = 1) {
            collection.bulkWrite(any<List<WriteModel<Document>>>(), any<BulkWriteOptions>())
        }
    }
}
