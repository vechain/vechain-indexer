package org.vechain.indexer.blocks

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse

/** A page plus the timestamp it settled at, or null while an incoming block could change it. */
data class BlockRange(val page: PaginatedResponse<IndexedBlock>, val settledAt: Long?)

@Profile("blocks")
@Service
open class BlockService(private val repository: BlocksReadRepository) {

    /** Always newest-first: [from] is an inclusive upper bound, defaulting to the indexed head. */
    open fun getBlocks(from: Long?, size: Int?): BlockRange {
        val pageSize = size ?: DEFAULT_PAGE_SIZE
        val rows = repository.findBlocks(from, pageSize + 1)
        val data = rows.take(pageSize)
        val hasNext = rows.size > pageSize

        return BlockRange(
            page =
                paginatedResponse(
                    data = data,
                    hasNext = hasNext,
                    cursor = nextCursor(data, hasNext),
                ),
            settledAt = settledAt(from, data),
        )
    }

    /** The head never settles — mid-backfill its newest row looks old but is not final. */
    internal fun settledAt(from: Long?, data: List<IndexedBlock>): Long? {
        if (from == null) return null
        val newest = data.firstOrNull() ?: return null
        if (newest.blockNumber != from) return null
        if (newest.blockNumber - data.last().blockNumber + 1 != data.size.toLong()) return null
        return newest.blockTimestamp
    }

    /** The next `from`: the bound is inclusive, so step one block below the last row. */
    private fun nextCursor(data: List<IndexedBlock>, hasNext: Boolean): String? {
        if (!hasNext) return null
        val last = data.lastOrNull() ?: return null
        return (last.blockNumber - 1).toString()
    }
}
