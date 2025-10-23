package org.vechain.indexer.stargate.token

interface LevelledValue<T> {
    val total: T
    val byLevel: Map<TokenLevel, T>

    fun valueForLevel(level: TokenLevel?): T
}
