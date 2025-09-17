package org.vechain.indexer.stargate

enum class TokenLevel {
    All,
    Strength,
    Thunder,
    Mjolnir,
    VeThorX,
    StrengthX,
    ThunderX,
    MjolnirX,
    Dawn,
    Lightning,
    Flash;

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
