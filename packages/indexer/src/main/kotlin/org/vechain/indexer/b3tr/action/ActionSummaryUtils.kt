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
import org.vechain.indexer.b3tr.ProofUtils
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.utils.scaleDown

object ActionSummaryUtils {

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
    fun getTo(event: IndexedEvent): String =
        event.params.getAsString("to") ?: error("Missing param 'to' in event: ${event.id}")

    fun getFrom(event: IndexedEvent): String =
        event.params.getAsString("from") ?: error("Missing param 'from' in event: ${event.id}")

    fun getValue(event: IndexedEvent): BigDecimal =
        event.params.getAsBigInteger("value")?.let { scaleDown(it, 18) }
            ?: error("Missing param 'value' in event: ${event.id}")

    fun getAppId(event: IndexedEvent): String =
        event.params.getAsString("appId") ?: error("Missing param 'appId' in event: ${event.id}")

    fun getProof(event: IndexedEvent): SustainabilityProofV2? =
        event.params.getAsString("proof")?.let { ProofUtils.parseProofFromJson(it) }

    fun getAction(event: IndexedEvent): Action {
        val appId = getAppId(event)
        val to = getTo(event)
        val from = getFrom(event)
        val value = getValue(event)
        val proof = getProof(event)

        return Action(
            appId = appId,
            receiver = to,
            distributor = from,
            amount = value,
            proof = proof,
        )
    }

    fun getEntity(event: IndexedEvent, entityType: EntityType): String =
        when (entityType) {
            EntityType.USER -> getTo(event)
            EntityType.APP -> getAppId(event)
            EntityType.GLOBAL -> "GLOBAL"
        }

    fun groupByTo(events: List<IndexedEvent>): Map<String, List<IndexedEvent>> =
        events
            .map {
                it.params.getAsString("to")?.let { to -> to.lowercase(Locale.getDefault()) to it }
                    ?: error("Missing 'to' in event: ${it.id}")
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, proposalEvents) -> proposalEvents.sortedBy { it.blockNumber } }

    fun groupByAppId(events: List<IndexedEvent>): Map<String, List<IndexedEvent>> =
        events
            .map {
                it.params.getAsString("appId")?.let { appId ->
                    appId.lowercase(Locale.getDefault()) to it
                } ?: error("Missing 'appId' in event: ${it.id}")
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, appEvents) -> appEvents.sortedBy { it.blockNumber } }
}
