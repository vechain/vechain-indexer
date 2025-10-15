package org.vechain.indexer.stargate

import java.math.BigDecimal

enum class TokenLevel(val vetRequired: BigDecimal?) {
    All(null),
    Strength(BigDecimal("1000000")),
    Thunder(BigDecimal("5000000")),
    Mjolnir(BigDecimal("15000000")),
    VeThorX(BigDecimal("600000")),
    StrengthX(BigDecimal("1600000")),
    ThunderX(BigDecimal("5600000")),
    MjolnirX(BigDecimal("15600000")),
    Dawn(BigDecimal("25000")),
    Lightning(BigDecimal("50000")),
    Flash(BigDecimal("200000"));

    companion object {
        /**
         * Parse a string to find the corresponding TokenLevel. All is not considered a valid level.
         */
        fun fromString(value: String?): TokenLevel? =
            when (value?.trim()?.lowercase()) {
                "strength" -> Strength
                "thunder" -> Thunder
                "mjolnir" -> Mjolnir
                "vethorx" -> VeThorX
                "strengthx" -> StrengthX
                "thunderx" -> ThunderX
                "mjolnirx" -> MjolnirX
                "dawn" -> Dawn
                "lightning" -> Lightning
                "flash" -> Flash
                else -> null // All is not a valid level for parsing
            }

        /**
         * Get the TokenLevel by its ordinal value. Returns null if the ordinal is out of bounds or
         * 0 (All).
         */
        fun fromOrdinal(ordinal: Int): TokenLevel? =
            if (ordinal in 1 until entries.size) {
                entries[ordinal]
            } else {
                null // Returns null for All or out of bounds
            }
    }
}
