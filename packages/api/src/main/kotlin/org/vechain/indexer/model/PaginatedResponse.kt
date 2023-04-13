package org.vechain.indexer.model

data class PaginatedResponse<T>(
    val data: T? = null,
    val pagination: PaginationDetail? = null
)

data class PaginationDetail(
    val totalPages: Int? = null,
    val totalElements: Long? = null
)