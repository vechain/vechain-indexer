package org.vechain.indexer.config

enum class VeChainNetwork(val value: String) {
    MAINNET("main"),
    TESTNET("test"),
    CUSTOM("custom");

    override fun toString(): String = value
}
