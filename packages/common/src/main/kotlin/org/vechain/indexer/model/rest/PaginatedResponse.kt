package org.vechain.indexer.model.rest

data class PaginatedResponse<T>(
    val data: T? = null,
    val pagination: PaginationDetail? = null
)

data class PaginationDetail(
    val totalPages: Int? = null,
    val totalElements: Long? = null
)