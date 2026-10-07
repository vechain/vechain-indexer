package org.vechain.indexer.wov.marketplace

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.exception.WindowNotIndexedException
import org.vechain.indexer.rest.PaginationDetail
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.CursorPaginationUtils

/** Resolves a window against how far the index is, then pages its buyers by address. */
@Profile("wov-marketplace")
@Service
open class WovBuyerStatsService(private val repository: WovMarketplaceReadRepository) {

    /**
     * [to] falls back to the cursor's window end, then to the indexed-through timestamp; a window
     * past that is refused rather than answered in part. The cursor carries the end so every page
     * of a live request sees the same window.
     */
    open fun buyers(from: Long, to: Long?, size: Int?, cursor: String?): WovBuyerStatsResponse {
        val position = CursorPaginationUtils.parseCursor(cursor)
        val cursorTo = position?.let { it.sortValue.toLongOrNull() ?: throw invalidCursor() }
        val after = position?.cursorValue?.let(::cursorAddress)
        if (to != null && cursorTo != null && to != cursorTo) {
            throw BadRequestException("to=$to disagrees with the cursor's window end $cursorTo")
        }
        val indexedThrough = repository.indexedThrough()
        val end = to ?: cursorTo ?: indexedThrough ?: throw WindowNotIndexedException(null, null)
        if (from >= end) throw BadRequestException("from=$from must be before to=$end")
        if (indexedThrough == null || end > indexedThrough) {
            throw WindowNotIndexedException(end, indexedThrough)
        }

        val pageSize = size ?: DEFAULT_PAGE_SIZE
        val rows = repository.buyers(from, end, after, pageSize + 1)
        val page = rows.take(pageSize)
        val hasNext = rows.size > pageSize
        return WovBuyerStatsResponse(
            from = from,
            to = end,
            data = page.map(WovBuyerStats::from),
            pagination =
                PaginationDetail(
                    hasNext = hasNext,
                    cursor =
                        if (hasNext) CursorPaginationUtils.generateCursor(end, page.last().buyer)
                        else null,
                ),
        )
    }

    private fun cursorAddress(value: String): String =
        if (Address(value).isValid()) HexUtils.normalise(value) else throw invalidCursor()

    private fun invalidCursor() = BadRequestException("Invalid cursor format")
}
