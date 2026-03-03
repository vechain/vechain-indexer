package org.vechain.indexer.b3tr.treasury

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

@ExtendWith(MockKExtension::class)
internal class TreasuryTransferServiceTest {

    @MockK lateinit var repository: TreasuryTransferRepository
    @MockK lateinit var businessEventProperties: BusinessEventProperties

    private lateinit var service: TreasuryTransferService

    companion object {
        const val TREASURY = "0xtreasury"
        const val B3TR = "0xb3tr"
        const val EMISSIONS = "0xemissions"
        const val X_ALLOC_POOL = "0xxallocpool"
        const val DBA_POOL = "0xdbapool"
        const val GM_NFT = "0xgmnft"
        const val VOTER_REWARDS = "0xvoterrewards"
        const val X2EARN_REWARDS = "0xx2earnrewards"
        const val GRANTS_MANAGER = "0xgrantsmanager"
        const val GOVERNANCE_TIMELOCK = "0xgovernancetimelock"
        const val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"
        const val UNKNOWN_ADDRESS = "0xunknown"

        val SUBSTITUTIONS =
            mapOf(
                "TREASURY_CONTRACT" to TREASURY,
                "B3TR_CONTRACT" to B3TR,
                "EMISSIONS" to EMISSIONS,
                "X_ALLOC_POOL_CONTRACT" to X_ALLOC_POOL,
                "B3TR_DBA_POOL_CONTRACT" to DBA_POOL,
                "GM_NFT_CONTRACT" to GM_NFT,
                "VOTER_REWARDS_CONTRACT" to VOTER_REWARDS,
                "X2EARN_REWARDS_POOL_CONTRACT" to X2EARN_REWARDS,
                "GRANTS_MANAGER_CONTRACT" to GRANTS_MANAGER,
                "GOVERNANCE_TIMELOCK_CONTRACT" to GOVERNANCE_TIMELOCK,
            )
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { businessEventProperties.substitutions } returns SUBSTITUTIONS
        service = TreasuryTransferService(repository, businessEventProperties)
    }

    private fun transferIn(
        from: String,
        to: String = TREASURY,
        value: String = "1000",
        txId: String = "tx-1",
        id: String = "e1",
        blockNumber: Long = 100L,
    ) =
        buildIndexedEvent(
            id = id,
            blockNumber = blockNumber,
            blockTimestamp = 1000L,
            txId = txId,
            eventType = "B3TR_TreasuryTransferIn",
            params =
                AbiEventParameters(
                    returnValues = mapOf("from" to from, "to" to to, "value" to value)
                ),
        )

    private fun transferOut(
        from: String = TREASURY,
        to: String,
        value: String = "1000",
        txId: String = "tx-1",
        id: String = "e1",
        blockNumber: Long = 100L,
    ) =
        buildIndexedEvent(
            id = id,
            blockNumber = blockNumber,
            blockTimestamp = 1000L,
            txId = txId,
            eventType = "B3TR_TreasuryTransferOut",
            params =
                AbiEventParameters(
                    returnValues = mapOf("from" to from, "to" to to, "value" to value)
                ),
        )

    private fun gmUpgradeEvent(txId: String = "tx-1", newLevel: String = "2", id: String = "u1") =
        buildIndexedEvent(
            id = id,
            txId = txId,
            eventType = "B3TR_TreasuryGmUpgrade",
            params =
                AbiEventParameters(
                    returnValues =
                        mapOf("tokenId" to "42", "oldLevel" to "1", "newLevel" to newLevel)
                ),
        )

    @Test
    fun `processEvents returns empty list for empty input`() {
        assertEquals(emptyList<TreasuryTransfer>(), service.processEvents(emptyList()))
    }

    @Test
    fun `classifies GM upgrade with matching Upgraded event`() {
        val txId = "tx-gm"
        val events =
            listOf(
                transferIn(from = UNKNOWN_ADDRESS, txId = txId, id = "e1"),
                gmUpgradeEvent(txId = txId, newLevel = "2"),
            )

        val result = service.processEvents(events)

        assertEquals(1, result.size)
        assertEquals(TreasuryTransferCategory.GM_UPGRADE, result[0].category)
        assertEquals("GM upgrade to Moon", result[0].label)
    }

    @Test
    fun `classifies GM upgrade to Galaxy level`() {
        val txId = "tx-gm-galaxy"
        val events =
            listOf(
                transferIn(from = UNKNOWN_ADDRESS, txId = txId, id = "e1"),
                gmUpgradeEvent(txId = txId, newLevel = "10"),
            )

        val result = service.processEvents(events)

        assertEquals(TreasuryTransferCategory.GM_UPGRADE, result[0].category)
        assertEquals("GM upgrade to Galaxy", result[0].label)
    }

    @Test
    fun `classifies GM upgrade with invalid level as Earth`() {
        val txId = "tx-gm-bad"
        val events =
            listOf(
                transferIn(from = UNKNOWN_ADDRESS, txId = txId, id = "e1"),
                gmUpgradeEvent(txId = txId, newLevel = "999"),
            )

        val result = service.processEvents(events)

        assertEquals(TreasuryTransferCategory.GM_UPGRADE, result[0].category)
        assertEquals("GM upgrade to Earth", result[0].label)
    }

    @Test
    fun `classifies emission from zero address`() {
        val events = listOf(transferIn(from = ZERO_ADDRESS))

        val result = service.processEvents(events)

        assertEquals(1, result.size)
        assertEquals(TreasuryTransferCategory.EMISSION, result[0].category)
        assertEquals("Weekly emission", result[0].label)
    }

    @Test
    fun `classifies emission from emissions contract`() {
        val events = listOf(transferIn(from = EMISSIONS))

        val result = service.processEvents(events)

        assertEquals(TreasuryTransferCategory.EMISSION, result[0].category)
        assertEquals("Weekly emission", result[0].label)
        assertEquals("Emissions", result[0].counterpartyName)
    }

    @Test
    fun `classifies surplus from x-allocation pool`() {
        val events = listOf(transferIn(from = X_ALLOC_POOL))

        val result = service.processEvents(events)

        assertEquals(TreasuryTransferCategory.SURPLUS, result[0].category)
        assertEquals("App voting surplus", result[0].label)
        assertEquals("X-Allocation Pool", result[0].counterpartyName)
    }

    @Test
    fun `classifies surplus from DBA pool`() {
        val events = listOf(transferIn(from = DBA_POOL))

        val result = service.processEvents(events)

        assertEquals(TreasuryTransferCategory.SURPLUS, result[0].category)
        assertEquals("App voting surplus", result[0].label)
    }

    @Test
    fun `classifies grant funding when sent to grants manager`() {
        val events = listOf(transferOut(to = GRANTS_MANAGER))

        val result = service.processEvents(events)

        assertEquals(TreasuryTransferCategory.GRANT, result[0].category)
        assertEquals("Grant funding", result[0].label)
        assertEquals("Grants", result[0].counterpartyName)
    }

    @Test
    fun `classifies governance transfer when sent to timelock`() {
        val events = listOf(transferOut(to = GOVERNANCE_TIMELOCK))

        val result = service.processEvents(events)

        assertEquals(TreasuryTransferCategory.OUT, result[0].category)
        assertEquals("Governance transfer", result[0].label)
        assertEquals("Governance Timelock", result[0].counterpartyName)
    }

    @Test
    fun `classifies OUT for treasury sending to unknown address`() {
        val events = listOf(transferOut(to = UNKNOWN_ADDRESS))

        val result = service.processEvents(events)

        assertEquals(TreasuryTransferCategory.OUT, result[0].category)
        assertEquals("B3TR Sent", result[0].label)
        assertNull(result[0].counterpartyName)
    }

    @Test
    fun `classifies OUT for treasury sending to known address`() {
        val events = listOf(transferOut(to = VOTER_REWARDS))

        val result = service.processEvents(events)

        assertEquals(TreasuryTransferCategory.OUT, result[0].category)
        assertEquals("B3TR Sent", result[0].label)
        assertEquals("Voter Rewards", result[0].counterpartyName)
    }

    @Test
    fun `classifies OTHER for unknown sender to treasury`() {
        val events = listOf(transferIn(from = UNKNOWN_ADDRESS))

        val result = service.processEvents(events)

        assertEquals(TreasuryTransferCategory.OTHER, result[0].category)
        assertEquals("B3TR Received", result[0].label)
        assertNull(result[0].counterpartyName)
    }

    @Test
    fun `sets counterpartyName for known sender to treasury`() {
        val events = listOf(transferIn(from = VOTER_REWARDS))

        val result = service.processEvents(events)

        assertEquals("Voter Rewards", result[0].counterpartyName)
    }

    @Test
    fun `counterpartyName distinguishes X-Allocation Pool from X2Earn Rewards Pool`() {
        val events =
            listOf(
                transferIn(from = X_ALLOC_POOL, id = "e1", txId = "tx-1"),
                transferIn(from = X2EARN_REWARDS, id = "e2", txId = "tx-2"),
            )

        val result = service.processEvents(events)

        assertEquals("X-Allocation Pool", result[0].counterpartyName)
        assertEquals("X2Earn Rewards Pool", result[1].counterpartyName)
    }

    @Test
    fun `ignores non-transfer events`() {
        val events = listOf(gmUpgradeEvent())

        val result = service.processEvents(events)

        assertEquals(0, result.size)
    }

    @Test
    fun `processes multiple transfers in one batch`() {
        val events =
            listOf(
                transferIn(from = ZERO_ADDRESS, id = "e1", txId = "tx-1"),
                transferOut(to = UNKNOWN_ADDRESS, id = "e2", txId = "tx-2"),
                transferIn(from = X_ALLOC_POOL, id = "e3", txId = "tx-3"),
            )

        val result = service.processEvents(events)

        assertEquals(3, result.size)
        assertEquals(TreasuryTransferCategory.EMISSION, result[0].category)
        assertEquals(TreasuryTransferCategory.OUT, result[1].category)
        assertEquals(TreasuryTransferCategory.SURPLUS, result[2].category)
    }

    @Test
    fun `GM upgrade takes priority over emission classification`() {
        val txId = "tx-gm-emission"
        val events =
            listOf(
                transferIn(from = ZERO_ADDRESS, txId = txId, id = "e1"),
                gmUpgradeEvent(txId = txId, newLevel = "5"),
            )

        val result = service.processEvents(events)

        assertEquals(TreasuryTransferCategory.GM_UPGRADE, result[0].category)
        assertEquals("GM upgrade to Mars", result[0].label)
    }

    @Test
    fun `generates unique ids for different transfers`() {
        val events =
            listOf(
                transferIn(from = ZERO_ADDRESS, id = "e1", txId = "tx-1"),
                transferIn(from = ZERO_ADDRESS, id = "e2", txId = "tx-2"),
            )

        val result = service.processEvents(events)

        assertEquals(2, result.size)
        assert(result[0].id != result[1].id)
    }

    @Test
    fun `populates block fields from event`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    blockId = "block-abc",
                    blockNumber = 42L,
                    blockTimestamp = 9999L,
                    txId = "tx-1",
                    eventType = "B3TR_TreasuryTransferIn",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf("from" to ZERO_ADDRESS, "to" to TREASURY, "value" to "500")
                        ),
                )
            )

        val result = service.processEvents(events)

        assertEquals("block-abc", result[0].blockId)
        assertEquals(42L, result[0].blockNumber)
        assertEquals(9999L, result[0].blockTimestamp)
        assertEquals("tx-1", result[0].txId)
        assertEquals("500", result[0].value)
    }
}
