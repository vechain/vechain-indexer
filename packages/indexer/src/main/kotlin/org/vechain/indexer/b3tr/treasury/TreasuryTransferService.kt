package org.vechain.indexer.b3tr.treasury

import java.math.BigInteger
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.b3tr.gm.GmLevelName
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsString

@Service
@Profile("b3tr", "b3tr-treasury")
open class TreasuryTransferService(
    private val repository: TreasuryTransferRepository,
    private val businessEventProperties: BusinessEventProperties,
) {

    private val zeroAddress = "0x0000000000000000000000000000000000000000"

    private fun knownAddresses(): Map<String, String> {
        val sub = businessEventProperties.substitutions
        return mapOf(
                (sub["TREASURY_CONTRACT"] ?: "").lowercase() to "Treasury",
                (sub["B3TR_CONTRACT"] ?: "").lowercase() to "B3TR",
                (sub["EMISSIONS"] ?: "").lowercase() to "Emissions",
                (sub["X_ALLOC_POOL_CONTRACT"] ?: "").lowercase() to "X-Allocation Pool",
                (sub["GM_NFT_CONTRACT"] ?: "").lowercase() to "Galaxy Member",
                (sub["VOTER_REWARDS_CONTRACT"] ?: "").lowercase() to "Voter Rewards",
                (sub["X2EARN_REWARDS_POOL_CONTRACT"] ?: "").lowercase() to "X-Allocation Pool",
            )
            .filter { it.key.isNotEmpty() }
    }

    private fun grantsManager(): String? =
        businessEventProperties.substitutions["GRANTS_MANAGER_CONTRACT"]?.lowercase()

    private fun governanceTimelock(): String? =
        businessEventProperties.substitutions["GOVERNANCE_TIMELOCK_CONTRACT"]?.lowercase()

    open fun processEvents(events: List<IndexedEvent>): List<TreasuryTransfer> {
        if (events.isEmpty()) return emptyList()

        val transferEvents =
            events.filter {
                it.eventType == "B3TR_TreasuryTransferIn" ||
                    it.eventType == "B3TR_TreasuryTransferOut"
            }
        val upgradeEvents = events.filter { it.eventType == "B3TR_TreasuryGmUpgrade" }

        val txIdToNewLevel: Map<String, GmLevelName> =
            upgradeEvents.associate { ev ->
                val newLevelStr =
                    ev.params.getAsString("newLevel")
                        ?: return@associate ev.txId to GmLevelName.EARTH
                val level =
                    runCatching { GmLevelName.map(BigInteger(newLevelStr)) }
                        .getOrElse { GmLevelName.EARTH }
                ev.txId to level
            }

        val treasury = businessEventProperties.substitutions["TREASURY_CONTRACT"]?.lowercase() ?: ""
        val known = knownAddresses()
        val grants = grantsManager()
        val timelock = governanceTimelock()

        return transferEvents.map { ev ->
            val from = ev.params.getAsString("from")?.lowercase() ?: ""
            val to = ev.params.getAsString("to")?.lowercase() ?: ""
            val value = ev.params.getAsString("value") ?: "0"

            val (category, label) =
                classify(
                    from = from,
                    to = to,
                    treasury = treasury,
                    txId = ev.txId,
                    txIdToNewLevel = txIdToNewLevel,
                    known = known,
                    grantsManager = grants,
                    governanceTimelock = timelock,
                )

            val counterparty = if (from == treasury) to else from
            val counterpartyName =
                known[counterparty]
                    ?: when {
                        counterparty == grants -> "Grants"
                        counterparty == timelock -> "Governance Timelock"
                        else -> null
                    }

            val id = DigestUtils.sha1Hex("${ev.txId}_${ev.id}_$from$to$value")
            TreasuryTransfer(
                id = id,
                blockId = ev.blockId,
                blockNumber = ev.blockNumber,
                blockTimestamp = ev.blockTimestamp,
                txId = ev.txId,
                from = from,
                to = to,
                value = value,
                category = category,
                label = label,
                counterpartyName = counterpartyName,
            )
        }
    }

    private fun classify(
        from: String,
        to: String,
        treasury: String,
        txId: String,
        txIdToNewLevel: Map<String, GmLevelName>,
        known: Map<String, String>,
        grantsManager: String?,
        governanceTimelock: String?,
    ): Pair<TreasuryTransferCategory, String> {
        val emissions = businessEventProperties.substitutions["EMISSIONS"]?.lowercase() ?: ""
        val xAllocPool =
            businessEventProperties.substitutions["X_ALLOC_POOL_CONTRACT"]?.lowercase() ?: ""
        val dbaPool =
            businessEventProperties.substitutions["B3TR_DBA_POOL_CONTRACT"]?.lowercase() ?: ""

        if (txIdToNewLevel.containsKey(txId)) {
            val level = txIdToNewLevel[txId]!!
            val levelLabel = levelNameToLabel(level)
            return TreasuryTransferCategory.GM_UPGRADE to "GM upgrade to $levelLabel"
        }
        if (from == zeroAddress || from == emissions) {
            return TreasuryTransferCategory.EMISSION to "Weekly emission"
        }
        if (from == xAllocPool || from == dbaPool) {
            return TreasuryTransferCategory.SURPLUS to "App voting surplus"
        }
        if (to == grantsManager) {
            return TreasuryTransferCategory.GRANT to "Grant funding"
        }
        if (to == governanceTimelock) {
            return TreasuryTransferCategory.OUT to "Governance transfer"
        }
        if (from == treasury) {
            return TreasuryTransferCategory.OUT to "B3TR Sent"
        }
        return TreasuryTransferCategory.OTHER to "B3TR Received"
    }

    private fun levelNameToLabel(level: GmLevelName): String =
        when (level) {
            GmLevelName.EARTH -> "Earth"
            GmLevelName.MOON -> "Moon"
            GmLevelName.MERCURY -> "Mercury"
            GmLevelName.VENUS -> "Venus"
            GmLevelName.MARS -> "Mars"
            GmLevelName.JUPITER -> "Jupiter"
            GmLevelName.SATURN -> "Saturn"
            GmLevelName.URANUS -> "Uranus"
            GmLevelName.NEPTUNE -> "Neptune"
            GmLevelName.GALAXY -> "Galaxy"
            else -> level.name.lowercase().replaceFirstChar { it.uppercase() }
        }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(records: List<TreasuryTransfer>) {
        if (records.isNotEmpty()) repository.saveAll(records)
    }
}
