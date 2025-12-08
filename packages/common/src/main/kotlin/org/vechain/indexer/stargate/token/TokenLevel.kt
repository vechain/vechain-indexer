package org.vechain.indexer.stargate.token

import java.math.BigDecimal

enum class TokenLevel(
    val staked: BigDecimal,
    val rewardMultiplier: BigDecimal,
    val weightMultiplier: BigDecimal,
) {
    All(BigDecimal("0"), BigDecimal("0"), BigDecimal("0")),
    Strength(BigDecimal("1000000"), BigDecimal("1.5"), BigDecimal("1")),
    Thunder(BigDecimal("5000000"), BigDecimal("2.5"), BigDecimal("1")),
    Mjolnir(BigDecimal("15000000"), BigDecimal("3.5"), BigDecimal("1")),
    VeThorX(BigDecimal("600000"), BigDecimal("2"), BigDecimal("1.5")),
    StrengthX(BigDecimal("1600000"), BigDecimal("3"), BigDecimal("1.5")),
    ThunderX(BigDecimal("5600000"), BigDecimal("4"), BigDecimal("1.5")),
    MjolnirX(BigDecimal("15600000"), BigDecimal("5"), BigDecimal("1.5")),
    Dawn(BigDecimal("10000"), BigDecimal("1"), BigDecimal("1")),
    Lightning(BigDecimal("50000"), BigDecimal("1.15"), BigDecimal("1")),
    Flash(BigDecimal("200000"), BigDecimal("1.3"), BigDecimal("1"));

    val effectiveStake: BigDecimal
        get() = staked.multiply(rewardMultiplier)

    val effectiveWeight: BigDecimal
        get() = staked.multiply(weightMultiplier)

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
