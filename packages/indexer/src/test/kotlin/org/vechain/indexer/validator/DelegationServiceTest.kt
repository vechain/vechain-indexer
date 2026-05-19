package org.vechain.indexer.validator

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.model.Block
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

class DelegationServiceTest {
    private lateinit var repository: DelegationRepository
    private lateinit var validatorRepository: ValidatorRepository
    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var service: DelegationService

    @BeforeEach
    fun setup() {
        repository = mockk()
        validatorRepository = mockk()
        mongoTemplate = mockk(relaxed = true)
        service =
            DelegationService(
                repository = repository,
                validatorRepository = validatorRepository,
                mongoTemplate = mongoTemplate,
                inlineVersioningProperties = InlineVersioningProperties(),
                stakerSC = STAKER_ADDRESS,
                validatorStartBlock = 0L,
            )
    }

    /**
     * Regression for Thor solo / dev-stack: genesis validators are ACTIVE with `startBlock = 0`
     * (Hayabusa from block 0). The original `start <= 0L` guard in `nextCycleStart` treated `0` as
     * "not activated" and left the delegation in zero-cycle state forever. After the fix the gate
     * is on validator status, so a delegation initiated against a solo genesis validator gets a
     * concrete `transitionAtBlock` and flips QUEUED→ACTIVE on the next cycle boundary.
     */
    @Test
    fun `DelegationInitiated against ACTIVE validator with startBlock 0 schedules transition at next cycle boundary`() {
        every { repository.findByTransitionAtBlockAndStatusIn(any(), any()) } returns emptyList()
        every { repository.findByTransitionAtBlockIsNullAndStatusIn(any()) } returns emptyList()
        every { repository.findByTokenIdIn(any()) } returns emptyList()
        every { repository.findByValidatorIn(any()) } returns emptyList()
        every { validatorRepository.findAllById(any<Iterable<String>>()) } returns
            listOf(soloGenesisValidator(VALIDATOR_ID))

        val (updates, archive) =
            runBlocking {
                service.processBlock(
                    block(number = 55),
                    listOf(delegationInitiatedEvent(blockNumber = 55)),
                )
            }

        expectThat(updates).hasSize(1).first().and {
            get { id }.isEqualTo("1")
            get { validator }.isEqualTo(VALIDATOR_ID)
            get { status }.isEqualTo(DelegationStatus.QUEUED)
            // start=0, period=90, blockNumber=55 → currentCycleStart=0 → next boundary=90
            get { transitionAtBlock }.isEqualTo(90L)
        }
        expectThat(archive).hasSize(0)
    }

    @Test
    fun `applyScheduledTransitions flips QUEUED to ACTIVE at scheduled transitionAtBlock`() {
        val existing =
            Delegation(
                id = "1",
                validator = VALIDATOR_ID,
                tokenId = "4",
                owner = "0xowner",
                status = DelegationStatus.QUEUED,
                tokenLevel = TokenLevel.Dawn,
                stakedAmount = "0",
                totalRewardsClaimed = java.math.BigInteger.ZERO,
                txId = "0xtx",
                transitionAtBlock = 90L,
                blockId = "0xblock-prev",
                blockNumber = 55,
                blockTimestamp = 100,
            )
        every {
            repository.findByTransitionAtBlockAndStatusIn(
                90L,
                listOf(DelegationStatus.QUEUED, DelegationStatus.EXITING),
            )
        } returns listOf(existing)
        every { repository.findByTransitionAtBlockIsNullAndStatusIn(any()) } returns emptyList()
        every { repository.findByTokenIdIn(any()) } returns emptyList()
        every { repository.findByValidatorIn(any()) } returns emptyList()
        every { validatorRepository.findAllById(any<Iterable<String>>()) } returns
            listOf(soloGenesisValidator(VALIDATOR_ID))

        val (updates, _) = runBlocking { service.processBlock(block(number = 90), emptyList()) }

        expectThat(updates).hasSize(1).first().and {
            get { id }.isEqualTo("1")
            get { status }.isEqualTo(DelegationStatus.ACTIVE)
            get { transitionAtBlock }.isEqualTo(null)
        }
    }

    private fun delegationInitiatedEvent(blockNumber: Long): IndexedEvent =
        buildIndexedEvent(
            id = "evt-1",
            blockId = "0xblock-$blockNumber",
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 10,
            txId = "0xtx",
            origin = "0xowner",
            address = STARGATE_DELEGATION_ADDRESS,
            eventType = "DelegationInitiated",
            params =
                AbiEventParameters(
                    returnValues =
                        mapOf(
                            "delegationId" to "1",
                            "tokenId" to "4",
                            "validator" to VALIDATOR_ID,
                            "levelId" to TokenLevel.Dawn.ordinal.toString(),
                            "amount" to "0",
                        )
                ),
        )

    private fun soloGenesisValidator(id: String): Validator =
        Validator(
            id = id,
            blockId = "0xvblock",
            blockNumber = 607,
            blockTimestamp = 6070,
            status = Status.ACTIVE,
            cyclePeriodLength = 90L,
            startBlock = 0L,
            completedPeriods = 6L,
        )

    private fun block(number: Long): Block =
        Block(
            id = "0x" + number.toString().padStart(64, '0'),
            number = number,
            timestamp = number * 10,
            parentID = "0x" + (number - 1).toString().padStart(64, '0'),
            size = 1,
            gasLimit = 1,
            baseFeePerGas = "0x0",
            beneficiary = "0xbeneficiary",
            gasUsed = 1,
            totalScore = 1,
            txsRoot = "0x" + "2".repeat(64),
            txsFeatures = 0,
            stateRoot = "0x" + "3".repeat(64),
            receiptsRoot = "0x" + "4".repeat(64),
            com = false,
            signer = "0x0000000000000000000000000000000000000001",
            isTrunk = true,
            isFinalized = true,
            transactions = emptyList(),
        )

    private companion object {
        const val STAKER_ADDRESS = "0x00000000000000000000000000005374616B6572"
        const val STARGATE_DELEGATION_ADDRESS = "0x1234567890123456789012345678901234567890"
        const val VALIDATOR_ID = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
    }
}
