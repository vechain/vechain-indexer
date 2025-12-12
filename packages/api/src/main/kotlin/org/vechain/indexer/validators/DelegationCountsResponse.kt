package org.vechain.indexer.validators

data class DelegationCountsResponse(
    val validator: String,
    val queued: Long,
    val active: Long,
    val exiting: Long,
)
