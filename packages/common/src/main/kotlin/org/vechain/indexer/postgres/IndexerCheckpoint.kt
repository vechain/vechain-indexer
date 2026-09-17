package org.vechain.indexer.postgres

/** One indexer's resume point: the newest block whose write it has committed. */
data class IndexerCheckpoint(
    val schema: String,
    val version: Int,
    val blockNumber: Long?,
    val blockId: String?,
)
