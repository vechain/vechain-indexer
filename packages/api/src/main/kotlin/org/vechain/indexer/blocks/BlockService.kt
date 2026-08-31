package org.vechain.indexer.blocks

import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.blocks.repository.BlockRepository
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.gradedMaxAge
import org.vechain.indexer.rest.paginatedResponse

/** A page plus how long it may be cached, or null while an incoming block could change it. */
data class BlockRange(val page: PaginatedResponse<IndexedBlock>, val maxAgeSeconds: Long?)

@Profile("blocks")
@Service
open class BlockService(private val repository: BlockRepository) {

    /** Always newest-first: [from] is an inclusive upper bound, defaulting to the indexed head. */
    open fun getBlocks(from: Long?, size: Int?): BlockRange {
        val pageable = PageRequest.of(0, size ?: DEFAULT_PAGE_SIZE)
        val slice =
            if (from == null) {
                repository.findLatest(pageable)
            } else {
                repository.findAtOrBelow(from, pageable)
            }

        return BlockRange(
            page =
                paginatedResponse(
                    data = slice.content,
                    hasNext = slice.hasNext(),
                    cursor = nextCursor(slice),
                ),
            maxAgeSeconds = maxAgeSeconds(from, slice.content, Instant.now().epochSecond),
        )
    }

    /**
     * The head range is never cacheable: its content advances with the index, and during a
     * from-genesis backfill its newest row carries an old timestamp that would otherwise read as
     * settled. An anchored range is settled once it starts exactly at [from] and spans its rows
     * without a gap — a missing block would fill in later and change the response.
     *
     * The TTL is then the age of the newest row, so it never outlives the span over which the page
     * has already been stable. That bounds a reorg to poisoning an entry for at most its own depth.
     */
    internal fun maxAgeSeconds(
        from: Long?,
        data: List<IndexedBlock>,
        nowEpochSeconds: Long,
    ): Long? {
        if (from == null) return null
        val newest = data.firstOrNull() ?: return null
        if (newest.blockNumber != from) return null
        if (newest.blockNumber - data.last().blockNumber + 1 != data.size.toLong()) return null
        return gradedMaxAge(newest.blockTimestamp, nowEpochSeconds)
    }

    /** The next `from`: the bound is inclusive, so step one block below the last row. */
    private fun nextCursor(slice: Slice<IndexedBlock>): String? {
        if (!slice.hasNext()) return null
        val last = slice.content.lastOrNull() ?: return null
        return (last.blockNumber - 1).toString()
    }
}
