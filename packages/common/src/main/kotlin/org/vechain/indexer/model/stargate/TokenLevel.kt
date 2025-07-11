package org.vechain.indexer.model.stargate

enum class TokenLevel {
    None,
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
         * Parse a string to find the corresponding TokenLevel. None is not considered a valid
         * level.
         */
        fun fromString(value: String?): TokenLevel? {
            return when (value?.trim()?.lowercase()) {
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
                else -> null // None is not a valid level for parsing
            }
        }

        /**
         * Get the TokenLevel by its ordinal value. Returns null if the ordinal is out of bounds or
         * 0 (None).
         */
        fun fromOrdinal(ordinal: Int): TokenLevel? {
            return if (ordinal in 1 until entries.size) {
                entries[ordinal]
            } else {
                null // Returns null for None or out of bounds
            }
        }
    }
}
