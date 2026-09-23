package org.vechain.indexer.stargate.token

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent

internal class StargateTokenServiceTest {
    private val repository = mockk<StargateTokenWriteRepository>()
    private val service =
        StargateTokenService(
            repository,
            StargateEventService(stargateDelegationContract = "0xdelegation"),
        )

    @Test
    fun `processEvents keeps only the tokens an event changed`() {
        val managed = stargateToken("34132", manager = "0xc5213085d3fc19b6a883a92a5703f7733360f063")
        val untouched = stargateToken("34133")
        every { repository.findAllById(setOf("34132", "34133")) } returns listOf(managed, untouched)

        val updated =
            service.processEvents(listOf(managerRemovedEvent, delegationInitiated("34133")))

        assertEquals(listOf("34132"), updated.map { it.tokenId })
        assertNull(updated.single().manager)
        assertEquals(24407827L, updated.single().blockNumber)
    }

    @Test
    fun `processEvents skips the read when no event names a token`() {
        assertEquals(emptyList<StargateToken>(), service.processEvents(emptyList()))
        verify(exactly = 0) { repository.findAllById(any()) }
    }

    private fun stargateToken(tokenId: String, manager: String? = null) =
        StargateToken(
            tokenId = tokenId,
            level = TokenLevel.Dawn,
            owner = "0xowner",
            manager = manager,
            totalRewardsClaimed = BigInteger.ZERO,
            totalBootstrapRewardsClaimed = BigInteger.ZERO,
            vetStaked = BigInteger("10000"),
            migrated = false,
            boosted = false,
            blockNumber = 24407826,
            blockId = "0xprev",
            blockTimestamp = 1767463000,
        )

    private fun delegationInitiated(tokenId: String) =
        managerRemovedEvent.copy(
            id = "0xdelegation-0",
            eventType = "DelegationInitiated",
            params =
                AbiEventParameters(
                    returnValues = mapOf("tokenId" to tokenId, "validator" to "0xvalidator"),
                    eventType = "DelegationInitiated",
                ),
        )

    private val managerRemovedEvent =
        IndexedEvent(
            id = "0x32a27b5c414da4e4c405d79da4ad97f2b745fae332cd535bf8dedb59c706da26-0",
            blockId = "0x" + "0".repeat(63) + "1",
            blockNumber = 24407827,
            blockTimestamp = 1767463010,
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
}
