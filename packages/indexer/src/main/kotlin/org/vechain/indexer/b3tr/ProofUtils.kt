package org.vechain.indexer.b3tr

import kotlin.jvm.java
import kotlin.run
import kotlin.text.isEmpty
import org.slf4j.LoggerFactory
import org.vechain.indexer.b3tr.action.Impact
import org.vechain.indexer.b3tr.action.ProofV2
import org.vechain.indexer.b3tr.action.SustainabilityProofV1
import org.vechain.indexer.b3tr.action.SustainabilityProofV2
import org.vechain.indexer.utils.JsonUtils

object ProofUtils {
    private val logger = LoggerFactory.getLogger(ProofUtils::class.java)

    fun parseProofFromJson(proofJson: String): SustainabilityProofV2? {
        if (proofJson.isEmpty()) {
            return null
        }

        // Attempt to parse as V2. If that fails attempt to parse as V1. Then finally use the
        // fallback parser.
        return parseProofV2(proofJson)
            ?: run { parseProofV1(proofJson) ?: run { fallbackParser(proofJson) } }
    }

    fun parseProofV1(proofJson: String): SustainabilityProofV2? {
        try {
            val proofV1 = JsonUtils.mapper.readValue(proofJson, SustainabilityProofV1::class.java)
            return mapV1ToV2(proofV1)
        } catch (e: Exception) {
            logger.debug("Failed to map proof json to V1: $proofJson")
            return null
        }
    }

    fun parseProofV2(proofJson: String): SustainabilityProofV2? {
        try {
            // Pre-process JSON to handle string values for proof and impact fields
            val cleanedJson = cleanJsonForV2Parsing(proofJson)

            val proofV2 = JsonUtils.mapper.readValue(cleanedJson, SustainabilityProofV2::class.java)
            // If there is no version field then it is not a V2 proof
            if (proofV2.version == 0) {
                return null
            }

            // Convert empty objects to null for proof and impact
            val cleanedProof = if (isEmptyProof(proofV2.proof)) null else proofV2.proof
            val cleanedImpact = if (isEmptyImpact(proofV2.impact)) null else proofV2.impact

            return proofV2.copy(proof = cleanedProof, impact = cleanedImpact)
        } catch (e: Exception) {
            logger.debug("Failed to map proof json to V2: $proofJson")
            return null
        }
    }

    /**
     * Clean JSON by converting string values for "proof" and "impact" fields to null. This handles
     * cases where these fields are incorrectly set as strings (e.g., "proof": "{}") instead of
     * objects.
     */
    private fun cleanJsonForV2Parsing(json: String): String {
        // Use regex to replace string values for proof and impact with null
        var cleaned = json
        // Replace "proof": "..." with "proof": null
        cleaned = cleaned.replace(Regex(""""proof"\s*:\s*"[^"]*""""), """"proof": null""")
        // Replace "impact": "..." with "impact": null
        cleaned = cleaned.replace(Regex(""""impact"\s*:\s*"[^"]*""""), """"impact": null""")
        return cleaned
    }

    private fun isEmptyProof(proof: ProofV2?): Boolean {
        if (proof == null) return false
        return proof.image == null &&
            proof.link == null &&
            proof.text == null &&
            proof.video == null
    }

    private fun isEmptyImpact(impact: Impact?): Boolean {
        if (impact == null) return true
        return impact.carbon == null &&
            impact.water == null &&
            impact.energy == null &&
            impact.waste_mass == null &&
            impact.waste_items == null &&
            impact.waste_reduction == null &&
            impact.biodiversity == null &&
            impact.people == null &&
            impact.timber == null &&
            impact.plastic == null &&
            impact.education_time == null &&
            impact.trees_planted == null &&
            impact.calories_burned == null &&
            impact.clean_energy_production_wh == null &&
            impact.sleep_quality_percentage == null
    }

    private fun fallbackParser(proofJson: String): SustainabilityProofV2 {
        logger.info(
            "Using fallback parser for proof as it is not a valid V1 or V2 proof: $proofJson"
        )

        return SustainabilityProofV2(
            version = 0,
            description = proofJson,
            proof = null,
            impact = null,
        )
    }

    fun mapV1ToV2(proofV1: SustainabilityProofV1): SustainabilityProofV2 {
        var innerProof: ProofV2? = null
        if (proofV1.proof != null) {
            var image: String? = null
            var link: String? = null
            var text: String? = null
            var video: String? = null
            if (proofV1.proof?.proof_type == "image" || proofV1.proof?.proof_type == "photo") {
                image = proofV1.proof?.proof_data
            } else if (proofV1.proof?.proof_type == "link") {
                link = proofV1.proof?.proof_data
            } else if (proofV1.proof?.proof_type == "text") {
                text = proofV1.proof?.proof_data
            } else if (proofV1.proof?.proof_type == "video") {
                video = proofV1.proof?.proof_data
            } else {
                text = "${proofV1.proof?.proof_type}: ${proofV1.proof?.proof_data}"
            }

            innerProof = ProofV2(image = image, link = link, text = text, video = video)
        }
        val proofV2 =
            SustainabilityProofV2(
                version = 1,
                description = proofV1.metadata?.description,
                proof = innerProof,
                impact = proofV1.impact,
            )
        return proofV2
    }
}
