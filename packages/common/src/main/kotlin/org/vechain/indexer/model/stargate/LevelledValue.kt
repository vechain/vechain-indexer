package org.vechain.indexer.model.stargate

interface LevelledValue<T> {
    val total: T
    val byLevel: Map<Int, T>

    fun valueForLevel(levelId: Int?): T
}
