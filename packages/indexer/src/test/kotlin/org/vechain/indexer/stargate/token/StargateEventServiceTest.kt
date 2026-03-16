package org.vechain.indexer.stargate.token

import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.ValidatorDelegationService

class StargateEventServiceTest {
    private val service =
        StargateEventService(
            validatorDelegationService = mockk<ValidatorDelegationService>(relaxed = true),
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
            validatorSnapshots = emptyMap(),
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
                validatorSnapshots = emptyMap(),
                existingTokens = existingTokens,
            )

            assertThat(latestTokenSnapshots[token.tokenId]!!.manager).isNull()
            assertThat(latestTokenSnapshots[token.tokenId]!!.version).isEqualTo(2)
            assertThat(existingTokens).containsExactly(token)
        }

    private fun token(manager: String?) =
        StargateToken(
            tokenId = "35112",
            level = TokenLevel.Dawn,
            owner = "0xowner",
            manager = manager,
            delegationStatus = Status.NONE,
            validatorId = null,
            totalRewardsClaimed = BigInteger.ZERO,
            totalBootstrapRewardsClaimed = BigInteger.ZERO,
            vetStaked = BigInteger("10000"),
            migrated = false,
            boosted = false,
            blockNumber = 23693226,
            blockId = "0xprev",
            blockTimestamp = 1767463000,
            version = 1,
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
}
