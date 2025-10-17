package org.vechain.indexer.b3tr.action

import org.springframework.boot.context.properties.bind.ConstructorBinding

data class Metadata @ConstructorBinding constructor(val description: String? = null)

data class SustainabilityProofV1
@ConstructorBinding
constructor(
    val app_name: String,
    val action_type: String,
    val metadata: Metadata? = null,
    val proof: ProofV1? = null,
    val impact: Impact? = null,
)

data class SustainabilityProofV2
@ConstructorBinding
constructor(
    val version: Int,
    val description: String? = null,
    val proof: ProofV2? = null,
    val impact: Impact? = null,
)

data class ProofV1 @ConstructorBinding constructor(val proof_type: String, val proof_data: String)

data class ProofV2
@ConstructorBinding
constructor(val image: String? = null, val link: String?, val text: String?, val video: String?)

data class Impact
@ConstructorBinding
constructor(
    val carbon: Long? = null, // grams reduced or removed of CO2
    val water: Long? = null, // ml of water saved
    val energy: Long? = null, // Wh of energy saved
    val waste_mass: Long? = null, // grams of waste diverted from landfills
    val waste_items: Long? = null, // legacy, number of items recycled
    val waste_reduction: Long? = null, // legacy
    val biodiversity: Long? = null, // legacy, meters square of land restored
    val people: Long? = null, // legacy, number of community members benefited
    val timber: Long? = null, // grams of timber saved
    val plastic: Long? = null, // grams of plastic saved
    val education_time: Long? = null, // minutes of education
    val trees_planted: Long? = null, // number of trees planted
    val calories_burned: Long? = null, // amount of kcal burned
    val clean_energy_production_wh: Long? = null, // Wh of clean energy produced
    val sleep_quality_percentage: Long? = null, // percentage of sleep quality
)
