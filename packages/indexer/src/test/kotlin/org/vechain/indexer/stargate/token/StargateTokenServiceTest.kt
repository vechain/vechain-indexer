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
