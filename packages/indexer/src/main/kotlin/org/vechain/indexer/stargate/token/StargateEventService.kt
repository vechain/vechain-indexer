package org.vechain.indexer.stargate.token

import java.math.BigInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsBoolean
import org.vechain.indexer.utils.ParamUtils.getAsString

/**
 * Applies Stargate NFT events to token snapshots: mint, burn, transfer, manager, boost, rewards.
 */
@Profile("stargate", "stargate-token")
@Service
class StargateEventService(
    @Value("\${business-event.substitutions.STARGATE_DELEGATION_CONTRACT}")
    private val stargateDelegationContract: String
) {
    /** Apply event-driven mutations to token snapshots. */
    fun handleStargateEvents(
        events: List<IndexedEvent>,
        latestTokenSnapshots: MutableMap<String, StargateToken>,
    ) {
        val groupedEvents =
            events
                .groupBy(::tokenIdOf)
                .filterKeys { it != null } // skip events without either ID
                .mapValues { (_, tokenEvents) ->
                    tokenEvents.sortedWith(
                        compareByDescending<IndexedEvent> { it.eventType == "TokenMinted" }
                            .thenBy { it.blockNumber }
                    )
                }

        groupedEvents.forEach { (tokenId, tokenEvents) ->
            val id = tokenId ?: return@forEach // safety check
            var current: StargateToken? = latestTokenSnapshots[id]

            tokenEvents.forEach { event -> current = processEvent(event, id, current) }
            current?.let { latestTokenSnapshots[id] = it }
        }
    }

    // Process a single event for a token
    private fun processEvent(
        event: IndexedEvent,
        tokenId: String,
        base: StargateToken?,
    ): StargateToken? {
        fun required(): StargateToken = requireBaseToken(base, event, tokenId)
        return when (event.eventType) {
            "TokenMinted" -> handleTokenMinted(event, tokenId)
            "TokenBurned" -> handleTokenUnstaked(event, required())
            "Transfer" -> handleTokenTransfer(event, required())
            "TokenManagerAdded" -> handleManagerAdded(event, required())
            "TokenManagerRemoved" -> handleManagerRemoved(event, required())
            "MaturityPeriodBoosted" -> handleTokenBoosted(event, required())
            "NodeDelegated" -> handleNodeManagementEvent(event, base)
            "BaseVTHORewardsClaimed",
            "DelegationRewardsClaimed" -> handleRewardsClaimed(event, required())
            else -> base
        }
    }

    private fun requireBaseToken(
        base: StargateToken?,
        event: IndexedEvent,
        tokenId: String,
    ): StargateToken =
        base
            ?: throw IllegalStateException(
                "No StargateToken loaded for ${event.eventType} event: tokenId=$tokenId, " +
                    "blockNumber=${event.blockNumber}, blockId=${event.blockId}, " +
                    "txId=${event.txId}, contract=${event.address}. " +
                    "The token was not present in the stargate_token collection at this block, " +
                    "so the event cannot be applied. Likely causes: the preceding TokenMinted " +
                    "event was never indexed (check INDEXER_START_BLOCK_STARGATE_TOKEN and the " +
                    "indexer's history), the token id is wrong in the event, or the token was " +
                    "deleted out-of-band."
            )

    // ------------------------------------------------------------------------
    // Event Handlers
    // ------------------------------------------------------------------------

    // Legacy node management event handler
    private fun handleNodeManagementEvent(
        event: IndexedEvent,
        base: StargateToken?,
    ): StargateToken? =
        if (base == null) {
            null
        } else if (event.params.getAsBoolean("delegated") == true) {
            handleManagerAdded(event, base)
        } else {
            handleManagerRemoved(event, base)
        }

    // Rewards claimed event
    private fun handleManagerAdded(
        event: IndexedEvent,
        base: StargateToken,
    ): StargateToken {
        return base.copy(
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            manager =
                event.params.getAsString("manager")
                    ?: event.params.getAsString(
                        "delegatee"
                    ), // TODO: Remove once Hayabusa live on Mainnet
        )
    }

    // Token manager removed event
    fun handleManagerRemoved(
        event: IndexedEvent,
        base: StargateToken,
    ): StargateToken {
        return base.copy(
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            manager = null,
        )
    }

    // Rewards claimed event
    fun handleRewardsClaimed(
        event: IndexedEvent,
        base: StargateToken,
    ): StargateToken {
        if (event.address == stargateDelegationContract) {
            val rewards =
                if (event.eventType == "BaseVTHORewardsClaimed") {
                    event.params.getAsBigInteger("amount")!!
                } else {
                    event.params.getAsBigInteger("rewards")!!
                }
            return base.copy(
                totalBootstrapRewardsClaimed = base.totalBootstrapRewardsClaimed + rewards,
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
            )
        } else {
            return base.copy(
                totalRewardsClaimed =
                    base.totalRewardsClaimed + event.params.getAsBigInteger("amount")!!,
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
            )
        }
    }

    // Token was boosted to skip maturity period
    private fun handleTokenBoosted(
        event: IndexedEvent,
        base: StargateToken,
    ): StargateToken {
        return base.copy(
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            boosted = true,
        )
    }

    // Token transfer event
    private fun handleTokenTransfer(
        event: IndexedEvent,
        base: StargateToken,
    ): StargateToken {
        return base.copy(
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            owner = event.params.getAsString("to")!!,
        )
    }

    // TokenBurned event is used for unstaking tokens
    private fun handleTokenUnstaked(
        event: IndexedEvent,
        base: StargateToken,
    ): StargateToken {
        return base.copy(
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            owner = Address.ZERO_ADDRESS,
            manager = null,
            vetStaked = BigInteger.ZERO,
        )
    }

    // TokenMinted event is used for staking tokens
    private fun handleTokenMinted(event: IndexedEvent, tokenId: String): StargateToken =
        StargateToken(
            tokenId = tokenId,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            owner = event.params.getAsString("owner")!!,
            level = TokenLevel.fromOrdinal(event.params.getAsString("levelId")!!.toInt())!!,
            vetStaked = event.params.getAsBigInteger("vetAmountStaked")!!,
            migrated = event.params.getAsBoolean("migrated")!!,
            totalRewardsClaimed = BigInteger.ZERO,
            totalBootstrapRewardsClaimed = BigInteger.ZERO,
            boosted = false,
        )

    companion object {
        fun tokenIdOf(event: IndexedEvent): String? =
            event.params.getAsString("tokenId")?.takeIf { it.isNotBlank() }
                ?: event.params.getAsString("nodeId") // TODO: Remove once Hayabusa live on Mainnet
    }
}
