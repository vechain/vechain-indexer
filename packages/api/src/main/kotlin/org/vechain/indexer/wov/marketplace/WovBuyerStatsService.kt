package org.vechain.indexer.wov.marketplace

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.exception.WindowNotIndexedException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.CursorPaginationUtils

/** Resolves a window against how far the index is, then pages its buyers by address. */
@Profile("wov-marketplace")
@Service
open class WovBuyerStatsService(private val repository: WovMarketplaceReadRepository) {

    /** A window past the indexed head is refused, never answered in part. */
    open fun buyers(
        from: Long,
        to: Long,
        size: Int?,
        cursor: String?,
    ): PaginatedResponse<WovBuyerStats> {
        val position = CursorPaginationUtils.parseCursor(cursor)
        val cursorTo = position?.let { it.sortValue.toLongOrNull() ?: throw invalidCursor() }
        val after = position?.cursorValue?.let(::cursorAddress)
        if (cursorTo != null && to != cursorTo) {
            throw BadRequestException("to=$to disagrees with the cursor's window end $cursorTo")
        }
        if (from >= to) throw BadRequestException("from=$from must be before to=$to")
        val indexedThrough = repository.indexedThrough()
        if (indexedThrough == null || to > indexedThrough) {
            throw WindowNotIndexedException(to, indexedThrough)
        }

        val pageSize = size ?: DEFAULT_PAGE_SIZE
        val rows = repository.buyers(from, to, after, pageSize + 1)
        val page = rows.take(pageSize)
        val hasNext = rows.size > pageSize
        return paginatedResponse(
            data = page.map(WovBuyerStats::from),
            hasNext = hasNext,
            cursor =
                if (hasNext) CursorPaginationUtils.generateCursor(to, page.last().buyer) else null,
        )
    }

    private fun cursorAddress(value: String): String =
        if (Address(value).isValid()) HexUtils.normalise(value) else throw invalidCursor()

    private fun invalidCursor() = BadRequestException("Invalid cursor format")
}
