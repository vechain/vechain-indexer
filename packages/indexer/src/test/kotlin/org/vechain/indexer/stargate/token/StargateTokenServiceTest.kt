package org.vechain.indexer.stargate.token

import io.mockk.MockKAnnotations
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorDelegationService
import org.vechain.indexer.validator.ValidatorRepository

@ExtendWith(MockKExtension::class)
internal class StargateTokenServiceTest {
    @MockK lateinit var repository: StargateTokenRepository
    @MockK lateinit var eventService: StargateEventService
    @MockK lateinit var validatorDelegationService: ValidatorDelegationService
    @MockK lateinit var validatorRepository: ValidatorRepository
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
                validatorRepository,
                mongoTemplate,
                inlineVersioningProperties,
                validatorStartBlock = 0L,
            )

        every { repository.findAllById(any<Iterable<String>>()) } returns emptyList()
        every { repository.findByValidatorIdIn(any()) } returns emptyList()
        every { repository.findAllDistinctValidatorIds() } returns emptyList()
        every { validatorRepository.findByStatusNot(Status.WITHDRAWN) } returns emptyList()
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
            every { validatorRepository.findByStatusNot(Status.WITHDRAWN) } returns
                listOf(
                    validator(id = "0xvalidator", cyclePeriodLength = 720L, startBlock = 22090000L)
                )

            val (updated, existing) = service.processBlock(block, emptyList())

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
                    validatorId = "0xexitingval",
                    delegationStatus = Status.ACTIVE,
                )
            val block = block(number = 22083558)

            every {
                repository.findByDelegationNextPeriodAndDelegationStatusIn(any(), any())
            } returns emptyList()
            // 0xexitingval is still present -> not in removedValidators
            every { validatorRepository.findByStatusNot(Status.WITHDRAWN) } returns
                listOf(
                    validator(id = "0xexitingval", cyclePeriodLength = 720L, startBlock = 22000000L)
                )
            every { repository.findAllDistinctValidatorIds() } returns listOf("0xexitingval")

            // ValidationSignaledExit event triggers validator lifecycle lookup
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
                        AbiEventParameters(returnValues = mapOf("validator" to "0xexitingval")),
                    address = "0xcontract",
                    eventType = "ValidationSignaledExit",
                    clauseIndex = 0,
                    signature = null,
                )

            // Token loaded via findByValidatorIdIn(lifecycleValidators) but not mutated
            every { repository.findByValidatorIdIn(setOf("0xexitingval")) } returns
                listOf(unchangedToken)

            val (updated, existing) = service.processBlock(block, listOf(exitEvent))

            // Token should be EXCLUDED: not modified and version > 1
            assertEquals(0, updated.size)
            assertEquals(0, existing.size)
        }

    @Test
    fun `processBlock clears manager for TokenManagerRemoved events`() = runBlocking {
        val realEventService =
            StargateEventService(
                validatorDelegationService = validatorDelegationService,
                validatorRepository = validatorRepository,
                stargateDelegationContract = "0xdelegation",
            )
        val realService =
            StargateTokenService(
                repository,
                realEventService,
                validatorRepository,
                mongoTemplate,
                inlineVersioningProperties,
                validatorStartBlock = 0L,
            )
        val existingToken =
            stargateToken(
                tokenId = "34132",
                version = 2,
                blockNumber = 24407826,
                delegationNextPeriod = null,
                validatorId = null,
                delegationStatus = Status.NONE,
                manager = "0xc5213085d3fc19b6a883a92a5703f7733360f063",
            )
        val block = block(number = 24407827)
        val managerRemovedEvent =
            IndexedEvent(
                id = "0x32a27b5c414da4e4c405d79da4ad97f2b745fae332cd535bf8dedb59c706da26-0",
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                txId = "0x32a27b5c414da4e4c405d79da4ad97f2b745fae332cd535bf8dedb59c706da26",
                origin = "0xc5213085d3fc19b6a883a92a5703f7733360f063",
                paid = "0x52b2b2ddccb489c",
                gasUsed = 35668,
                gasPayer = "0xc5213085d3fc19b6a883a92a5703f7733360f063",
                raw = null,
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "tokenId" to "34132",
                                "manager" to "0xc5213085d3fc19b6a883a92a5703f7733360f063",
                            ),
                        eventType = "TokenManagerRemoved",
                    ),
                address = "0x1856c533ac2d94340aaa8544d35a5c1d4a21dee7",
                eventType = "TokenManagerRemoved",
                clauseIndex = 0,
                signature = "0x2dea8fdc0115667de4800362c74206112df0a3a139fa2c217218b27a5da20259",
            )

        every { repository.findAllById(setOf("34132")) } returns listOf(existingToken)
        every { repository.findByDelegationNextPeriodAndDelegationStatusIn(any(), any()) } returns
            emptyList()
        every { validatorRepository.findByStatusNot(Status.WITHDRAWN) } returns emptyList()

        val (updated, existing) = realService.processBlock(block, listOf(managerRemovedEvent))

        assertEquals(1, updated.size)
        assertEquals(3, updated.single().version)
        assertNull(updated.single().manager)
        assertIterableEquals(listOf(existingToken), existing)
    }

    private fun stargateToken(
        tokenId: String,
        version: Int,
        blockNumber: Long,
        delegationNextPeriod: Long?,
        validatorId: String?,
        delegationStatus: Status,
        manager: String? = null,
    ) =
        StargateToken(
            tokenId = tokenId,
            level = TokenLevel.Dawn,
            owner = "0xowner",
            manager = manager,
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

    private fun validator(
        id: String,
        cyclePeriodLength: Long? = null,
        startBlock: Long? = null,
        exitBlock: Long? = null,
    ) =
        Validator(
            id = id,
            blockId = "0xblock",
            blockNumber = startBlock ?: 0L,
            blockTimestamp = 1000L,
            status = Status.ACTIVE,
            cyclePeriodLength = cyclePeriodLength,
            startBlock = startBlock,
            exitBlock = exitBlock,
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
