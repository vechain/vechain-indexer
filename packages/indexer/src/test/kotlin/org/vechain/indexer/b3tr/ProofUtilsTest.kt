package org.vechain.indexer.b3tr

import org.junit.jupiter.api.Test
import org.vechain.indexer.b3tr.sustainability.Impact
import org.vechain.indexer.b3tr.sustainability.Metadata
import org.vechain.indexer.b3tr.sustainability.ProofV1
import org.vechain.indexer.b3tr.sustainability.ProofV2
import org.vechain.indexer.b3tr.sustainability.SustainabilityProofV1
import org.vechain.indexer.b3tr.sustainability.SustainabilityProofV2
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class ProofUtilsTest {

    val VALID_PROOF_V1 =
        "{\"app_name\": \"NFBC\",\"action_type\": \"sustainable_literature_consumption\",\"proof\": {\"proof_type\": \"minutes_streamed\",\"proof_data\": \"1 minutes\"},\"impact\": {\"carbon\": \"8\",\"timber\": \"5\"},\"metadata\": {\"description\": \"Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print.\"}}"
    val VALID_PROOF_V2 =
        "{\"version\": 2,\"description\": \"Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print.\",\"proof\": {\"link\": \"link\", \"image\": \"image link\", \"video\": \"Video link\", \"text\": \"Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print.\"},\"impact\": {\"carbon\": 8,\"timber\": 5}}"

    @Test
    fun `parseProofFromJson - Should parse proof from v1 json`() {

        val result = ProofUtils.parseProofFromJson(VALID_PROOF_V1)

        expectThat(result)
            .isEqualTo(
                SustainabilityProofV2(
                    version = 1,
                    description =
                        "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print.",
                    proof =
                        ProofV2(
                            image = null,
                            link = null,
                            text = "minutes_streamed: 1 minutes",
                            video = null,
                        ),
                    impact = Impact(carbon = 8, timber = 5),
                )
            )
    }

    @Test
    fun `mapV1ToV2 - should map correctly`() {
        val result =
            ProofUtils.mapV1ToV2(
                SustainabilityProofV1(
                    app_name = "NFBC",
                    action_type = "sustainable_literature_consumption",
                    metadata =
                        Metadata(
                            description =
                                "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print."
                        ),
                    proof = ProofV1(proof_type = "minutes_streamed", proof_data = "1 minutes"),
                    impact = Impact(carbon = 8, timber = 5),
                )
            )

        expectThat(result)
            .isEqualTo(
                SustainabilityProofV2(
                    version = 1,
                    description =
                        "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print.",
                    proof =
                        ProofV2(
                            image = null,
                            link = null,
                            text = "minutes_streamed: 1 minutes",
                            video = null,
                        ),
                    impact = Impact(carbon = 8, timber = 5),
                )
            )
    }

    @Test
    fun `mapV1ToV2 - should map correctly with image proof`() {
        val result =
            ProofUtils.mapV1ToV2(
                SustainabilityProofV1(
                    app_name = "NFBC",
                    action_type = "sustainable_literature_consumption",
                    metadata =
                        Metadata(
                            description =
                                "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print."
                        ),
                    proof = ProofV1(proof_type = "image", proof_data = "image link"),
                    impact = Impact(carbon = 8, timber = 5),
                )
            )

        expectThat(result)
            .isEqualTo(
                SustainabilityProofV2(
                    version = 1,
                    description =
                        "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print.",
                    proof = ProofV2(image = "image link", link = null, text = null, video = null),
                    impact = Impact(carbon = 8, timber = 5),
                )
            )
    }

    @Test
    fun `mapV1ToV2 - should map correctly with photo proof`() {
        val result =
            ProofUtils.mapV1ToV2(
                SustainabilityProofV1(
                    app_name = "NFBC",
                    action_type = "sustainable_literature_consumption",
                    metadata =
                        Metadata(
                            description =
                                "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print."
                        ),
                    proof = ProofV1(proof_type = "photo", proof_data = "image link"),
                    impact = Impact(carbon = 8, timber = 5),
                )
            )

        expectThat(result)
            .isEqualTo(
                SustainabilityProofV2(
                    version = 1,
                    description =
                        "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print.",
                    proof = ProofV2(image = "image link", link = null, text = null, video = null),
                    impact = Impact(carbon = 8, timber = 5),
                )
            )
    }

    @Test
    fun `mapV1ToV2 - should map correctly with link proof`() {
        val result =
            ProofUtils.mapV1ToV2(
                SustainabilityProofV1(
                    app_name = "NFBC",
                    action_type = "sustainable_literature_consumption",
                    metadata =
                        Metadata(
                            description =
                                "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print."
                        ),
                    proof = ProofV1(proof_type = "link", proof_data = "link"),
                    impact = Impact(carbon = 8, timber = 5),
                )
            )

        expectThat(result)
            .isEqualTo(
                SustainabilityProofV2(
                    version = 1,
                    description =
                        "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print.",
                    proof = ProofV2(image = null, link = "link", text = null, video = null),
                    impact = Impact(carbon = 8, timber = 5),
                )
            )
    }

    @Test
    fun `mapV1ToV2 - should map correctly with video proof`() {
        val result =
            ProofUtils.mapV1ToV2(
                SustainabilityProofV1(
                    app_name = "NFBC",
                    action_type = "sustainable_literature_consumption",
                    metadata =
                        Metadata(
                            description =
                                "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print."
                        ),
                    proof = ProofV1(proof_type = "video", proof_data = "Video link"),
                    impact = Impact(carbon = 8, timber = 5),
                )
            )

        expectThat(result)
            .isEqualTo(
                SustainabilityProofV2(
                    version = 1,
                    description =
                        "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print.",
                    proof = ProofV2(image = null, link = null, text = null, video = "Video link"),
                    impact = Impact(carbon = 8, timber = 5),
                )
            )
    }

    @Test
    fun `mapV1ToV2 - should map correctly with text proof`() {
        val result =
            ProofUtils.mapV1ToV2(
                SustainabilityProofV1(
                    app_name = "NFBC",
                    action_type = "sustainable_literature_consumption",
                    metadata =
                        Metadata(
                            description =
                                "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print."
                        ),
                    proof = ProofV1(proof_type = "text", proof_data = "text data"),
                    impact = Impact(carbon = 8, timber = 5),
                )
            )

        expectThat(result)
            .isEqualTo(
                SustainabilityProofV2(
                    version = 1,
                    description =
                        "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print.",
                    proof = ProofV2(image = null, link = null, text = "text data", video = null),
                    impact = Impact(carbon = 8, timber = 5),
                )
            )
    }

    @Test
    fun `mapV1ToV2 - should map correctly with no description`() {
        val result =
            ProofUtils.mapV1ToV2(
                SustainabilityProofV1(
                    app_name = "NFBC",
                    action_type = "sustainable_literature_consumption",
                    metadata = null,
                    proof = ProofV1(proof_type = "text", proof_data = "text data"),
                    impact = Impact(carbon = 8, timber = 5),
                )
            )

        expectThat(result)
            .isEqualTo(
                SustainabilityProofV2(
                    version = 1,
                    description = null,
                    proof = ProofV2(image = null, link = null, text = "text data", video = null),
                    impact = Impact(carbon = 8, timber = 5),
                )
            )
    }

    @Test
    fun `parseProofV1 - Should parse proof from v1 json`() {

        val result = ProofUtils.parseProofV1(VALID_PROOF_V1)

        expectThat(result)
            .isEqualTo(
                SustainabilityProofV2(
                    version = 1,
                    description =
                        "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print.",
                    proof =
                        ProofV2(
                            image = null,
                            link = null,
                            text = "minutes_streamed: 1 minutes",
                            video = null,
                        ),
                    impact = Impact(carbon = 8, timber = 5),
                )
            )
    }

    @Test
    fun `parseProofFromJson - Should parse proof from v2 json`() {
        val result = ProofUtils.parseProofFromJson(VALID_PROOF_V2)

        expectThat(result)
            .isEqualTo(
                SustainabilityProofV2(
                    version = 2,
                    description =
                        "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print.",
                    proof =
                        ProofV2(
                            image = "image link",
                            link = "link",
                            text =
                                "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print.",
                            video = "Video link",
                        ),
                    impact = Impact(carbon = 8, timber = 5),
                )
            )
    }

    @Test
    fun `parseProofV2 - Should parse proof from v2 json`() {
        val result = ProofUtils.parseProofV2(VALID_PROOF_V2)

        expectThat(result)
            .isEqualTo(
                SustainabilityProofV2(
                    version = 2,
                    description =
                        "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print.",
                    proof =
                        ProofV2(
                            image = "image link",
                            link = "link",
                            text =
                                "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print.",
                            video = "Video link",
                        ),
                    impact = Impact(carbon = 8, timber = 5),
                )
            )
    }

    @Test
    fun `parseProofFromJson - Should return fallback proof if failed to parse JSON`() {
        val proofJson = "failed to parse"

        val result = ProofUtils.parseProofFromJson(proofJson)

        expectThat(result).isEqualTo(SustainabilityProofV2(version = 0, description = proofJson))
    }

    @Test
    fun `parseProofFromJson - Should return null if proofJson is empty`() {
        val proofJson = ""

        val result = ProofUtils.parseProofFromJson(proofJson)

        expectThat(result).isEqualTo(null)
    }
}
