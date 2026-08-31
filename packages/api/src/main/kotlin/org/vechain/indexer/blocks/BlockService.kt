package org.vechain.indexer.blocks

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.vechain.indexer.blocks.repository.BlockRepository
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse

@Profile("blocks")
@Service
open class BlockService(private val repository: BlockRepository) {

    open fun getBlocks(
        from: Long?,
        size: Int?,
        direction: String?,
    ): PaginatedResponse<IndexedBlock> {
        val pageSize = size ?: DEFAULT_PAGE_SIZE
        val sortDirection = resolveDirection(from, direction)
        val pageable = PageRequest.of(0, pageSize)

        val slice =
            when {
                from == null -> repository.findLatest(pageable)
                sortDirection.isAscending -> repository.findAtOrAbove(from, pageable)
                else -> repository.findAtOrBelow(from, pageable)
            }

        return paginatedResponse(
            data = slice.content,
            hasNext = slice.hasNext(),
            cursor = nextCursor(slice, sortDirection),
        )
    }

    /**
     * The head query is anchored at the indexed head, where only DESC is meaningful — ascending
     * from the head would start at blocks that do not exist yet. Callers wanting the oldest blocks
     * pass an explicit `from`.
     */
    internal fun resolveDirection(from: Long?, direction: String?): Sort.Direction {
        if (direction == null) {
            return if (from == null) Sort.Direction.DESC else Sort.Direction.ASC
        }
        val parsed =
            try {
                Sort.Direction.fromString(direction)
            } catch (_: IllegalArgumentException) {
                throw BadRequestException("Invalid sort direction param: $direction")
            }
        if (from == null && parsed.isAscending) {
            throw BadRequestException("direction=ASC requires a 'from' block number")
        }
        return parsed
    }

    /** The next `from` value: the ranges are inclusive, so step one block past the last row. */
    private fun nextCursor(slice: Slice<IndexedBlock>, direction: Sort.Direction): String? {
        if (!slice.hasNext()) return null
        val last = slice.content.lastOrNull() ?: return null
        return if (direction.isAscending) {
            (last.blockNumber + 1).toString()
        } else {
            (last.blockNumber - 1).toString()
        }
    }
}
