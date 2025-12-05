package org.vechain.indexer.b3tr.action

import java.math.BigDecimal
import java.util.Locale
import kotlin.collections.groupBy
import kotlin.collections.isNotEmpty
import kotlin.collections.map
import kotlin.collections.mapNotNull
import kotlin.collections.mapValues
import kotlin.collections.sumOf
import kotlin.takeIf
import org.slf4j.LoggerFactory
import org.vechain.indexer.b3tr.ProofUtils
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsInt
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.utils.scaleDown

object ActionSummaryUtils {
    private val logger = LoggerFactory.getLogger(ActionSummaryUtils::class.java)

    private fun exceeds(value: Long?, threshold: Long): Boolean {
        return value != null && value > threshold
    }

    /**
     * Validates if any field in the impact exceeds its specific threshold. Based on VeBetterDAO
     * documentation:
     * https://docs.vebetterdao.org/developer-guides/sustainability-proof-and-impacts#categories
     *
     * @param impact The impact to validate
     * @param config The configuration with specific thresholds for each impact type
     * @return true if any field exceeds its threshold, false otherwise
     */
    fun isImpactAboveThreshold(impact: Impact, config: ActionImpactConfig): Boolean {
        val checks =
            listOf(
                impact.carbon to config.carbon,
                impact.water to config.water,
                impact.energy to config.energy,
                impact.waste_mass to config.wasteMass,
                impact.timber to config.timber,
                impact.plastic to config.plastic,
                impact.education_time to config.educationTime,
                impact.trees_planted to config.treesPlanted,
                impact.calories_burned to config.caloriesBurned,
                impact.clean_energy_production_wh to config.cleanEnergyProductionWh,
                impact.sleep_quality_percentage to config.sleepQualityPercentage,
                impact.waste_items to config.wasteItems,
                impact.waste_reduction to config.wasteReduction,
                impact.biodiversity to config.biodiversity,
                impact.people to config.people,
            )

        return checks.any { (value, threshold) -> exceeds(value, threshold) }
    }

    /**
     * Validates and filters impacts based on specific thresholds per impact type. Logs a warning
     * for any impact exceeding its threshold. Based on VeBetterDAO documentation:
     * https://docs.vebetterdao.org/developer-guides/sustainability-proof-and-impacts#categories
     *
     * @param impacts List of impacts to validate
     * @param config The configuration with specific thresholds for each impact type
     * @return List of valid impacts that don't exceed their thresholds
     */
    fun validateAndFilterImpacts(impacts: List<Impact>, config: ActionImpactConfig): List<Impact> {
        return impacts.filter { impact ->
            val isValid = !isImpactAboveThreshold(impact, config)

            if (!isValid) {
                logger.warn("⚠️  Impact exceeds threshold. Impact $impact")
            }

            isValid
        }
    }

    // Accumulates impacts from a list of Impact objects.
    fun accumulateImpacts(impacts: List<Impact>): Impact? {
        if (impacts.isEmpty()) return null // Return null if no impacts are provided.

        // Helper function to sum a specific field from the impacts.
        fun sumField(selector: (Impact) -> Long?): Long? =
            impacts.mapNotNull(selector).takeIf { it.isNotEmpty() }?.sumOf { it }

        // Return a new Impact object with summed fields.
        return Impact(
            carbon = sumField { it.carbon },
            water = sumField { it.water },
            energy = sumField { it.energy },
            waste_mass = sumField { it.waste_mass },
            waste_items = sumField { it.waste_items },
            waste_reduction = sumField { it.waste_reduction },
            biodiversity = sumField { it.biodiversity },
            people = sumField { it.people },
            timber = sumField { it.timber },
            plastic = sumField { it.plastic },
            education_time = sumField { it.education_time },
            trees_planted = sumField { it.trees_planted },
            calories_burned = sumField { it.calories_burned },
            clean_energy_production_wh = sumField { it.clean_energy_production_wh },
            sleep_quality_percentage = sumField { it.sleep_quality_percentage },
        )
    }

    // Events
    fun getReceiver(event: IndexedEvent): String =
        event.params.getAsString("receiver")
            ?: error("Missing param 'receiver' in event: ${event.id}")

    fun getDistributor(event: IndexedEvent): String =
        event.params.getAsString("distributor")
            ?: error("Missing param 'distributor' in event: ${event.id}")

    fun getAmount(event: IndexedEvent): BigDecimal =
        event.params.getAsBigInteger("amount")?.let { scaleDown(it, 18) }
            ?: error("Missing param 'amount' in event: ${event.id}")

    fun getAppId(event: IndexedEvent): String =
        event.params.getAsString("appId") ?: error("Missing param 'appId' in event: ${event.id}")

    fun getProof(event: IndexedEvent): SustainabilityProofV2? =
        event.params.getAsString("proof")?.let { ProofUtils.parseProofFromJson(it) }

    fun getCycle(event: IndexedEvent): Int =
        event.params.getAsInt("cycle") ?: error("Missing param 'cycle' in event: ${event.id}")

    fun getAction(event: IndexedEvent): Action {
        val appId = getAppId(event)
        val receiver = getReceiver(event)
        val distributor = getDistributor(event)
        val value = getAmount(event)
        val proof = getProof(event)

        return Action(
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            blockId = event.blockId,
            appId = appId,
            receiver = receiver,
            distributor = distributor,
            amount = value,
            proof = proof,
        )
    }

    fun getEntity(event: IndexedEvent, entityType: EntityType): String =
        when (entityType) {
            EntityType.USER -> getReceiver(event)
            EntityType.APP -> getAppId(event)
            EntityType.GLOBAL -> EntityType.GLOBAL.name
        }

    fun groupByReceiver(events: List<IndexedEvent>): Map<String, List<IndexedEvent>> =
        events
            .map {
                it.params.getAsString("receiver")?.let { to ->
                    to.lowercase(Locale.getDefault()) to it
                } ?: error("Missing 'receiver' in event: ${it.id}")
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, proposalEvents) -> proposalEvents.sortedBy { it.blockNumber } }

    fun groupByDistributor(events: List<IndexedEvent>): Map<String, List<IndexedEvent>> =
        events
            .map {
                it.params.getAsString("distributor")?.let { from ->
                    from.lowercase(Locale.getDefault()) to it
                } ?: error("Missing 'distributor' in event: ${it.id}")
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, distributorEvents) -> distributorEvents.sortedBy { it.blockNumber } }

    fun groupByAppId(events: List<IndexedEvent>): Map<String, List<IndexedEvent>> =
        events
            .map {
                it.params.getAsString("appId")?.let { appId ->
                    appId.lowercase(Locale.getDefault()) to it
                } ?: error("Missing 'appId' in event: ${it.id}")
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, appEvents) -> appEvents.sortedBy { it.blockNumber } }

    fun assertEventTypes(event: List<IndexedEvent>, vararg allowedTypes: String) {
        event.forEach {
            if (it.eventType !in allowedTypes) {
                error(
                    "Unexpected event type: ${it.eventType}, expected one of: ${allowedTypes.joinToString()}"
                )
            }
        }
    }
}
