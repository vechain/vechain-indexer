package org.vechain.indexer.stargate.token

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.math.BigDecimal
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull

class TokenLevelDecimalValuesTest {
    private val objectMapper = ObjectMapper().registerKotlinModule()

    @Test
    fun `serializes nft level keys using legacy PascalCase names`() {
        val values =
            TokenLevelDecimalValues(
                Strength = BigDecimal("3.61053"),
                Thunder = BigDecimal("5.819445"),
                VeThorX = BigDecimal("4.818596"),
            )

        val tree = objectMapper.readTree(objectMapper.writeValueAsString(values))

        expectThat(tree.get("Strength"))
            .isNotNull()
            .get { decimalValue() }
            .isEqualTo(BigDecimal("3.61053"))
        expectThat(tree.get("Thunder"))
            .isNotNull()
            .get { decimalValue() }
            .isEqualTo(BigDecimal("5.819445"))
        expectThat(tree.get("VeThorX"))
            .isNotNull()
            .get { decimalValue() }
            .isEqualTo(BigDecimal("4.818596"))
        expectThat(tree.get("strength")).isNull()
        expectThat(tree.get("thunder")).isNull()
        expectThat(tree.get("veThorX")).isNull()
        expectThat(tree.fieldNames().asSequence().toSet())
            .isEqualTo(setOf("Strength", "Thunder", "VeThorX"))
    }
}
