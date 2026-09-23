package org.vechain.indexer.stargate.token

import java.math.BigInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures
import org.vechain.indexer.thor.Address

class StargateEventServiceTest {
    private val service = StargateEventService(stargateDelegationContract = "0xdelegation")

    @Test
    fun `handleStargateEvents clears manager for NodeDelegated removal`() {
        val token = token(manager = "0x3f90bf8b314c42005103b3c94505634fa680dcee")
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
        )

        assertThat(latestTokenSnapshots[token.tokenId]!!.manager).isNull()
    }

    @Test
    fun `handleStargateEvents clears manager when NodeDelegated omits delegated flag`() {
        val token = token(manager = "0x3f90bf8b314c42005103b3c94505634fa680dcee")
        val latestTokenSnapshots = mutableMapOf(token.tokenId to token)

        service.handleStargateEvents(
            events =
                listOf(
                    nodeDelegatedEvent(delegatee = "0x3f90bf8b314c42005103b3c94505634fa680dcee")
                ),
            latestTokenSnapshots = latestTokenSnapshots,
        )

        assertThat(latestTokenSnapshots[token.tokenId]!!.manager).isNull()
    }

    @Test
    fun `handleStargateEvents stamps block metadata for rewards claimed`() {
        val token = token()
        val latestTokenSnapshots = mutableMapOf(token.tokenId to token)
        val event = rewardsClaimedEvent()

        service.handleStargateEvents(
            events = listOf(event),
            latestTokenSnapshots = latestTokenSnapshots,
        )

        val updated = latestTokenSnapshots[token.tokenId]!!
        assertThat(updated.totalRewardsClaimed).isEqualTo(BigInteger("7"))
        assertThat(updated.blockId).isEqualTo(event.blockId)
        assertThat(updated.blockNumber).isEqualTo(event.blockNumber)
        assertThat(updated.blockTimestamp).isEqualTo(event.blockTimestamp)
    }

    @Test
    fun `handleStargateEvents burns a token without touching its rewards`() {
        val token = token(manager = "0xmanager").copy(totalRewardsClaimed = BigInteger.TWO)
        val latestTokenSnapshots = mutableMapOf(token.tokenId to token)

        service.handleStargateEvents(listOf(tokenBurnedEvent()), latestTokenSnapshots)

        val burned = latestTokenSnapshots.getValue(token.tokenId)
        assertThat(burned.owner).isEqualTo(Address.ZERO_ADDRESS)
        assertThat(burned.manager).isNull()
        assertThat(burned.vetStaked).isEqualTo(BigInteger.ZERO)
        assertThat(burned.totalRewardsClaimed).isEqualTo(BigInteger.TWO)
    }

    @Test
    fun `handleStargateEvents ignores the delegation events it no longer tracks`() {
        val token = token()
        val latestTokenSnapshots = mutableMapOf(token.tokenId to token)

        service.handleStargateEvents(listOf(delegationInitiatedEvent()), latestTokenSnapshots)

        assertThat(latestTokenSnapshots).containsExactlyEntriesOf(mapOf(token.tokenId to token))
    }

    private fun token(manager: String? = null) =
        StargateToken(
            tokenId = "35112",
            level = TokenLevel.Dawn,
            owner = "0xowner",
            manager = manager,
            totalRewardsClaimed = BigInteger.ZERO,
            totalBootstrapRewardsClaimed = BigInteger.ZERO,
            vetStaked = BigInteger("10000"),
            migrated = false,
            boosted = false,
            blockNumber = 23693226,
            blockId = "0xprev",
            blockTimestamp = 1767463000,
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

    private fun tokenBurnedEvent() =
        IndexedEventsFixtures.buildIndexedEvent(
            blockId = "0xburn",
            blockNumber = 23693230,
            blockTimestamp = 1767463040,
            eventType = "TokenBurned",
            params = AbiEventParameters(returnValues = mapOf("tokenId" to "35112")),
        )
}
