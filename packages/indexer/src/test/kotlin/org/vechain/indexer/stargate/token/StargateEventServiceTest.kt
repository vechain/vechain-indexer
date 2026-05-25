package org.vechain.indexer.stargate.token

import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorDelegationService
import org.vechain.indexer.validator.ValidatorRepository

class StargateEventServiceTest {
    private val validatorRepository = mockk<ValidatorRepository>(relaxed = true)
    private val service =
        StargateEventService(
            validatorDelegationService = mockk<ValidatorDelegationService>(relaxed = true),
            validatorRepository = validatorRepository,
            stargateDelegationContract = "0xdelegation",
        )

    @Test
    fun `handleStargateEvents clears manager for NodeDelegated removal`() = runBlocking {
        val token = token(manager = "0x3f90bf8b314c42005103b3c94505634fa680dcee")
        val existingTokens = mutableListOf<StargateToken>()
        val latestTokenSnapshots = mutableMapOf(token.tokenId to token)

        service.handleStargateEvents(
            events =
                listOf(
                    nodeDelegatedEvent(
                        delegated = false,
                        delegatee = "0x3f90bf8b314c42005103b3c94505634fa680dcee",
                    )
                ),
            latestTokenSnapshots = latestTokenSnapshots,
            validators = emptyMap(),
            existingTokens = existingTokens,
        )

        assertThat(latestTokenSnapshots[token.tokenId]!!.manager).isNull()
        assertThat(latestTokenSnapshots[token.tokenId]!!.version).isEqualTo(2)
        assertThat(existingTokens).containsExactly(token)
    }

    @Test
    fun `handleStargateEvents clears manager when NodeDelegated omits delegated flag`() =
        runBlocking {
            val token = token(manager = "0x3f90bf8b314c42005103b3c94505634fa680dcee")
            val existingTokens = mutableListOf<StargateToken>()
            val latestTokenSnapshots = mutableMapOf(token.tokenId to token)

            service.handleStargateEvents(
                events =
                    listOf(
                        nodeDelegatedEvent(delegatee = "0x3f90bf8b314c42005103b3c94505634fa680dcee")
                    ),
                latestTokenSnapshots = latestTokenSnapshots,
                validators = emptyMap(),
                existingTokens = existingTokens,
            )

            assertThat(latestTokenSnapshots[token.tokenId]!!.manager).isNull()
            assertThat(latestTokenSnapshots[token.tokenId]!!.version).isEqualTo(2)
            assertThat(existingTokens).containsExactly(token)
        }

    @Test
    fun `handleStargateEvents stamps block metadata for rewards claimed`() = runBlocking {
        val token = token()
        val existingTokens = mutableListOf<StargateToken>()
        val latestTokenSnapshots = mutableMapOf(token.tokenId to token)
        val event = rewardsClaimedEvent()

        service.handleStargateEvents(
            events = listOf(event),
            latestTokenSnapshots = latestTokenSnapshots,
            validators = emptyMap(),
            existingTokens = existingTokens,
        )

        val updated = latestTokenSnapshots[token.tokenId]!!
        assertThat(updated.totalRewardsClaimed).isEqualTo(BigInteger("7"))
        assertThat(updated.blockId).isEqualTo(event.blockId)
        assertThat(updated.blockNumber).isEqualTo(event.blockNumber)
        assertThat(updated.blockTimestamp).isEqualTo(event.blockTimestamp)
    }

    @Test
    fun `handleStargateEvents stamps block metadata for delegation initiated`() = runBlocking {
        val token = token()
        val existingTokens = mutableListOf<StargateToken>()
        val latestTokenSnapshots = mutableMapOf(token.tokenId to token)
        val event = delegationInitiatedEvent()
        val validators =
            mapOf(
                "0xvalidator" to
                    validator(
                        id = "0xvalidator",
                        cyclePeriodLength = 180L,
                        startBlock = 100L,
                        exitBlock = Long.MAX_VALUE,
                    )
            )

        service.handleStargateEvents(
            events = listOf(event),
            latestTokenSnapshots = latestTokenSnapshots,
            validators = validators,
            existingTokens = existingTokens,
        )

        val updated = latestTokenSnapshots[token.tokenId]!!
        assertThat(updated.delegationStatus).isEqualTo(Status.QUEUED)
        assertThat(updated.blockId).isEqualTo(event.blockId)
        assertThat(updated.blockNumber).isEqualTo(event.blockNumber)
        assertThat(updated.blockTimestamp).isEqualTo(event.blockTimestamp)
    }

    @Test
    fun `handleStargateEvents marks validator tokens exiting on validation signaled exit`() =
        runBlocking {
            val token =
                token(
                    delegationStatus = Status.ACTIVE,
                    validatorId = "0xvalidator",
                    delegationNextPeriod = 280L,
                    delegationPeriodLength = 180L,
                )
            val existingTokens = mutableListOf<StargateToken>()
            val latestTokenSnapshots = mutableMapOf(token.tokenId to token)
            val event = validationSignaledExitEvent()
            val validators =
                mapOf(
                    "0xvalidator" to
                        validator(
                            id = "0xvalidator",
                            cyclePeriodLength = 180L,
                            startBlock = 100L,
                            exitBlock = 460L,
                        )
                )

            service.handleStargateEvents(
                events = listOf(event),
                latestTokenSnapshots = latestTokenSnapshots,
                validators = validators,
                existingTokens = existingTokens,
            )

            val updated = latestTokenSnapshots[token.tokenId]!!
            assertThat(updated.delegationStatus).isEqualTo(Status.EXITING)
            assertThat(updated.delegationNextPeriod).isEqualTo(460L)
            assertThat(updated.validatorExiting).isEqualTo(true)
            assertThat(existingTokens).containsExactly(token)
        }

    @Test
    fun `handleStargateEvents ignores validation withdrawn`() = runBlocking {
        // ValidationWithdrawn fires whenever an endorser pulls previously-cooled-down stake
        // (post-`decreaseStake` / `signalExit`); the validator can stay ACTIVE through it.
        // True terminal withdrawal is detected by the periodic on-chain set diff
        // (StargateTokenService.checkMissingValidators + handleValidatorsDisappearedSnapshots),
        // not by this event.
        val token =
            token(
                delegationStatus = Status.EXITING,
                validatorId = "0xvalidator",
                delegationNextPeriod = 460L,
                delegationPeriodLength = 180L,
            )
        val existingTokens = mutableListOf<StargateToken>()
        val latestTokenSnapshots = mutableMapOf(token.tokenId to token)

        service.handleStargateEvents(
            events = listOf(validationWithdrawnEvent()),
            latestTokenSnapshots = latestTokenSnapshots,
            validators = emptyMap(),
            existingTokens = existingTokens,
        )

        assertThat(latestTokenSnapshots[token.tokenId]).isEqualTo(token)
        assertThat(existingTokens).isEmpty()
    }

    @Test
    fun `handleStargateEvents stamps block metadata for delegation withdrawn`() = runBlocking {
        val token = token(delegationStatus = Status.ACTIVE, validatorId = "0xvalidator")
        val existingTokens = mutableListOf<StargateToken>()
        val latestTokenSnapshots = mutableMapOf(token.tokenId to token)
        val event = delegationWithdrawnEvent()

        service.handleStargateEvents(
            events = listOf(event),
            latestTokenSnapshots = latestTokenSnapshots,
            validators = emptyMap(),
            existingTokens = existingTokens,
        )

        val updated = latestTokenSnapshots[token.tokenId]!!
        assertThat(updated.delegationStatus).isEqualTo(Status.NONE)
        assertThat(updated.validatorId).isNull()
        assertThat(updated.blockId).isEqualTo(event.blockId)
        assertThat(updated.blockNumber).isEqualTo(event.blockNumber)
        assertThat(updated.blockTimestamp).isEqualTo(event.blockTimestamp)
    }

    @Test
    fun `handleStargateEvents stamps block metadata for delegation exit requested`() = runBlocking {
        val token =
            token(
                delegationStatus = Status.ACTIVE,
                validatorId = "0xvalidator",
                delegationNextPeriod = 200L,
                delegationPeriodLength = 180L,
            )
        val existingTokens = mutableListOf<StargateToken>()
        val latestTokenSnapshots = mutableMapOf(token.tokenId to token)
        val event = delegationExitRequestedEvent()

        service.handleStargateEvents(
            events = listOf(event),
            latestTokenSnapshots = latestTokenSnapshots,
            validators = emptyMap(),
            existingTokens = existingTokens,
        )

        val updated = latestTokenSnapshots[token.tokenId]!!
        assertThat(updated.delegationStatus).isEqualTo(Status.EXITING)
        assertThat(updated.blockId).isEqualTo(event.blockId)
        assertThat(updated.blockNumber).isEqualTo(event.blockNumber)
        assertThat(updated.blockTimestamp).isEqualTo(event.blockTimestamp)
    }

    private fun token(
        manager: String? = null,
        delegationStatus: Status = Status.NONE,
        validatorId: String? = null,
        delegationNextPeriod: Long? = null,
        delegationPeriodLength: Long? = null,
    ) =
        StargateToken(
            tokenId = "35112",
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
            blockNumber = 23693226,
            blockId = "0xprev",
            blockTimestamp = 1767463000,
            version = 1,
            delegationNextPeriod = delegationNextPeriod,
            delegationPeriodLength = delegationPeriodLength,
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

    private fun nodeDelegatedEvent(delegated: Boolean? = null, delegatee: String) =
        IndexedEventsFixtures.buildIndexedEvent(
            id = "0x0b03b180b521e1c485360202ca0b8cab0d4d47e3cdded483567ff969d7c87653-0",
            blockId = "0x016987abac2c5724ae84399babc861ca95ae9ac6734ee0aa55aa2d98639e2a64",
            blockNumber = 23693227,
            blockTimestamp = 1767463010,
            txId = "0x0b03b180b521e1c485360202ca0b8cab0d4d47e3cdded483567ff969d7c87653",
            origin = "0x3f90bf8b314c42005103b3c94505634fa680dcee",
            params =
                AbiEventParameters(
                    returnValues =
                        buildMap {
                            put("nodeId", "35112")
                            put("delegatee", delegatee)
                            delegated?.let { put("delegated", it) }
                        },
                    eventType = "NodeDelegated",
                ),
            address = "0x1856c533ac2d94340aaa8544d35a5c1d4a21dee7",
            eventType = "NodeDelegated",
            clauseIndex = 0,
            signature = "0x2dea8fdc0115667de4800362c74206112df0a3a139fa2c217218b27a5da20259",
        )

    private fun rewardsClaimedEvent() =
        IndexedEventsFixtures.buildIndexedEvent(
            blockId = "0xreward",
            blockNumber = 23693228,
            blockTimestamp = 1767463020,
            eventType = "DelegationRewardsClaimed",
            address = "0xnot-delegation",
            params =
                AbiEventParameters(
                    returnValues = mapOf("tokenId" to "35112", "amount" to BigInteger("7"))
                ),
        )

    private fun delegationInitiatedEvent() =
        IndexedEventsFixtures.buildIndexedEvent(
            blockId = "0xdelegate",
            blockNumber = 23693229,
            blockTimestamp = 1767463030,
            eventType = "DelegationInitiated",
            params =
                AbiEventParameters(
                    returnValues = mapOf("tokenId" to "35112", "validator" to "0xvalidator")
                ),
        )

    private fun delegationWithdrawnEvent() =
        IndexedEventsFixtures.buildIndexedEvent(
            blockId = "0xwithdraw",
            blockNumber = 23693230,
            blockTimestamp = 1767463040,
            eventType = "DelegationWithdrawn",
            params = AbiEventParameters(returnValues = mapOf("tokenId" to "35112")),
        )

    private fun delegationExitRequestedEvent() =
        IndexedEventsFixtures.buildIndexedEvent(
            blockId = "0xexit",
            blockNumber = 23693231,
            blockTimestamp = 1767463050,
            eventType = "DelegationExitRequested",
            address = "0xstargate",
            params = AbiEventParameters(returnValues = mapOf("tokenId" to "35112")),
        )

    private fun validationSignaledExitEvent() =
        IndexedEventsFixtures.buildIndexedEvent(
            blockId = "0xvalidationexit",
            blockNumber = 23693232,
            blockTimestamp = 1767463060,
            eventType = "ValidationSignaledExit",
            params = AbiEventParameters(returnValues = mapOf("validator" to "0xvalidator")),
        )

    private fun validationWithdrawnEvent() =
        IndexedEventsFixtures.buildIndexedEvent(
            blockId = "0xvalidationwithdrawn",
            blockNumber = 23693233,
            blockTimestamp = 1767463070,
            eventType = "ValidationWithdrawn",
            params = AbiEventParameters(returnValues = mapOf("validator" to "0xvalidator")),
        )
}
