package org.vechain.indexer.fixtures

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.vechain.indexer.model.Block

object BlockFixtures {

    private val objectMapper = ObjectMapper()

    init {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.registerModule(
            KotlinModule.Builder()
                .withReflectionCacheSize(512)
                .configure(KotlinFeature.NullToEmptyCollection, false)
                .configure(KotlinFeature.NullToEmptyMap, false)
                .configure(KotlinFeature.NullIsSameAsDefault, false)
                .configure(KotlinFeature.StrictNullChecks, false)
                .build()
        )
    }

    val BLOCK_3_NO_CLAUSES = buildBlockFixture(3L)
    val BLOCK_4_SINGLE_CLAUSE = buildBlockFixture(4L)
    val BLOCK_5_VIP180_CONTRACTS = buildBlockFixture(5L)
    val BLOCK_6_VIP181_CONTRACTS = buildBlockFixture(6L)
    val BLOCK_8_MULTIPLE_CLAUSES = buildBlockFixture(8L)
    val BLOCK_16_MASTER_EVENT_UPDATE = buildBlockFixture(16L)

    private fun buildBlockFixture(blockNumber: Long): Block {
        return objectMapper.readValue(
            BlockFixtures::class.java.getResource("/block_${blockNumber}.json")!!.readText(),
            Block::class.java
        )
    }
}