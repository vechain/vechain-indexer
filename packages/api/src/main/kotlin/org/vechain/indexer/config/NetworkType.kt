package org.vechain.indexer.config

enum class NetworkType(val value: String) {
    MAIN("main"),
    TEST("test"),
    SOLO("solo"),
    OTHER("other");

    override fun toString(): String = value

    companion object {
        fun fromGenesisId(genesisId: String): NetworkType {
            if (genesisId.length < 2) {
                throw Exception("Invalid genesis id")
            }
            // Extract the last two characters of the string
            val lastTwoChars = genesisId.takeLast(2)

            // Parse the string as a hexadecimal number
            return when (lastTwoChars.toLong(16)) {
                74L -> MAIN
                39L -> TEST
                40L -> SOLO
                else -> OTHER
            }
        }
    }
}
