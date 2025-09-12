package org.vechain.indexer.b3tr

import kotlin.jvm.java
import kotlin.run
import kotlin.text.isEmpty
import org.slf4j.LoggerFactory
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
            val proofV2 = JsonUtils.mapper.readValue(proofJson, SustainabilityProofV2::class.java)
            // If there is no version field then it is not a V2 proof
            if (proofV2.version == 0) {
                return null
            }
            return proofV2
        } catch (e: Exception) {
            logger.debug("Failed to map proof json to V2: $proofJson")
            return null
        }
    }

    private fun fallbackParser(proofJson: String): SustainabilityProofV2 {
        logger.debug("Using fallback parser for proof")

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
