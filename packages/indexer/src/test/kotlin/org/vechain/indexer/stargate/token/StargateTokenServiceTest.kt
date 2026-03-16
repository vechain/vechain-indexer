package org.vechain.indexer.stargate.token

import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.ValidatorDelegationService
import org.vechain.indexer.validator.ValidatorSnapshot

@ExtendWith(MockKExtension::class)
internal class StargateTokenServiceTest {
    @MockK lateinit var repository: StargateTokenRepository
    @MockK lateinit var eventService: StargateEventService
    @MockK lateinit var validatorDelegationService: ValidatorDelegationService
    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var inlineVersioningProperties: InlineVersioningProperties

    private lateinit var service: StargateTokenService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service =
            StargateTokenService(
                repository,
                eventService,
                validatorDelegationService,
                mongoTemplate,
                inlineVersioningProperties,
            )

        every { repository.findAllById(any<Iterable<String>>()) } returns emptyList()
        every { repository.findByValidatorIdIn(any()) } returns emptyList()
        every { repository.findAllDistinctValidatorIds() } returns emptyList()
        coJustRun { eventService.handleStargateEvents(any(), any(), any(), any()) }
    }

    @Test
    fun `processBlock archives original snapshot when resolving unknown delegation start block`() =
        runBlocking {
            val existingToken =
                stargateToken(
                    tokenId = "15613",
                    version = 2,
                    blockNumber = 22083557,
                    delegationNextPeriod = 0L,
                    validatorId = "0xvalidator",
                    delegationStatus = Status.QUEUED,
                )
            val block = block(number = 22083558)

            every {
                repository.findByDelegationNextPeriodAndDelegationStatusIn(any(), any())
            } returns listOf(existingToken)
            coEvery { validatorDelegationService.decodeValidatorSnapshots(emptyList()) } returns
                mapOf(
                    "0xvalidator" to
                        ValidatorSnapshot(
                            validatorId = "0xvalidator",
                            stakingPeriodLength = 720L,
                            startBlock = 22090000L,
                            exitBlock = 0L,
                        )
                )

            val (updated, existing) = service.processBlock(block, emptyList(), emptyList())

            assertEquals(1, updated.size)
            assertEquals("15613", updated.single().tokenId)
            assertEquals(3, updated.single().version)
            assertEquals(22090000L, updated.single().delegationNextPeriod)
            assertIterableEquals(listOf(existingToken), existing)
        }

    @Test
    fun `processBlock excludes unmodified tokens loaded from DB at version greater than 1`() =
        runBlocking {
            // Token loaded via exitingValidators path but no mutation applies:
            // - Validator is NOT in removedValidators (still in current snapshots)
            // - Token status is ACTIVE with nextPeriod far in the future (no transition)
            // - eventService.handleStargateEvents is mocked to no-op
            val unchangedToken =
                stargateToken(
                    tokenId = "88888",
                    version = 5,
                    blockNumber = 22083400,
                    delegationNextPeriod = 99999999L,
                    validatorId = "0xexitingVal",
                    delegationStatus = Status.ACTIVE,
                )
            val block = block(number = 22083558)

            every {
                repository.findByDelegationNextPeriodAndDelegationStatusIn(any(), any())
            } returns emptyList()
            // 0xexitingVal is still present → not in removedValidators
            coEvery { validatorDelegationService.decodeValidatorSnapshots(emptyList()) } returns
                mapOf(
                    "0xexitingVal" to
                        ValidatorSnapshot(
                            validatorId = "0xexitingVal",
                            stakingPeriodLength = 720L,
                            startBlock = 22000000L,
                            exitBlock = 0L,
                        )
                )
            every { repository.findAllDistinctValidatorIds() } returns listOf("0xexitingVal")

            // ValidatorExitRequested event triggers exitingValidators lookup
            val exitEvent =
                IndexedEvent(
                    id = "evt1",
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    txId = "0xtx",
                    origin = "0xorigin",
                    paid = null,
                    gasUsed = null,
                    gasPayer = null,
                    raw = null,
                    params =
                        AbiEventParameters(returnValues = mapOf("validator" to "0xexitingVal")),
                    address = "0xcontract",
                    eventType = "ValidatorExitRequested",
                    clauseIndex = 0,
                    signature = null,
                )

            // Token loaded via findByValidatorIdIn(exitingValidators) but not mutated
            every { repository.findByValidatorIdIn(setOf("0xexitingVal")) } returns
                listOf(unchangedToken)

            val (updated, existing) = service.processBlock(block, emptyList(), listOf(exitEvent))

            // Token should be EXCLUDED: not modified and version > 1
            assertEquals(0, updated.size)
            assertEquals(0, existing.size)
        }

    private fun stargateToken(
        tokenId: String,
        version: Int,
        blockNumber: Long,
        delegationNextPeriod: Long?,
        validatorId: String?,
        delegationStatus: Status,
    ) =
        StargateToken(
            tokenId = tokenId,
            level = TokenLevel.Dawn,
            owner = "0xowner",
            delegationStatus = delegationStatus,
            validatorId = validatorId,
            totalRewardsClaimed = BigInteger.ZERO,
            totalBootstrapRewardsClaimed = BigInteger.ZERO,
            vetStaked = BigInteger("10000"),
            migrated = false,
            boosted = false,
            blockNumber = blockNumber,
            blockId = "0xprev",
            blockTimestamp = 1767463000,
            version = version,
            delegationNextPeriod = delegationNextPeriod,
            delegationPeriodLength = 720L,
        )

    private fun block(number: Long) =
        Block(
            id = "0x" + "0".repeat(63) + "1",
            number = number,
            timestamp = 1767463010,
            parentID = "0x" + "0".repeat(63) + "0",
            size = 0,
            gasLimit = 0,
            baseFeePerGas = null,
            beneficiary = "0xbeneficiary",
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
}
