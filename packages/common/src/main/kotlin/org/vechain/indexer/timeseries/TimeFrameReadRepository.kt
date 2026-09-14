package org.vechain.indexer.timeseries

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.vechain.indexer.accounts.TimeFrame

/** [TimeFrameRepo] over one series table, paged as the Mongo repositories were. */
abstract class TimeFrameReadRepository<T : TimeFrameDocument>(
    jdbc: JdbcTemplate,
    mapping: TimeFrameRowMapping<T>,
) : TimeFrameRepo<T> {

    private val series = TimeFrameTable(jdbc, mapping)

    override fun getLatestRecord(): T? = series.latest()

    override fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): T? =
        series
            .rows(
                "WHERE block_number <= :block ORDER BY block_number DESC LIMIT 1",
                MapSqlParameterSource("block", blockNumber),
            )
            .firstOrNull()

    override fun findLatestBeforeOrAtBlockTimestamp(blockTimestamp: Long): T? =
        series
            .rows(
                "WHERE block_timestamp <= :at ORDER BY block_timestamp DESC LIMIT 1",
                MapSqlParameterSource("at", blockTimestamp),
            )
            .firstOrNull()

    override fun findAll(pageable: Pageable): Slice<T> = page(null, pageable)

    override fun findByTimeFramesContains(timeFrame: TimeFrame, pageable: Pageable): Slice<T> =
        page(timeFrame, pageable)

    override fun findByBlockTimestampAfter(blockTimestamp: Long, pageable: Pageable): Slice<T> =
        page(null, pageable, after = blockTimestamp)

    override fun findByBlockTimestampBefore(blockTimestamp: Long, pageable: Pageable): Slice<T> =
        page(null, pageable, before = blockTimestamp)

    override fun findByBlockTimestampBetween(from: Long, to: Long, pageable: Pageable): Slice<T> =
        page(null, pageable, after = from, before = to)

    override fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<T> = page(timeFrame, pageable, after = blockTimestamp)

    override fun findByTimeFramesContainsAndBlockTimestampBefore(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<T> = page(timeFrame, pageable, before = blockTimestamp)

    override fun findByTimeFramesContainsAndBlockTimestampBetween(
        timeFrame: TimeFrame,
        from: Long,
        to: Long,
        pageable: Pageable,
    ): Slice<T> = page(timeFrame, pageable, after = from, before = to)

    /** Every row after [blockTimestamp], oldest first: the `historic/{range}` time series. */
    open fun findByBlockTimestampAfter(blockTimestamp: Long): List<T> =
        series.rows(where(null, blockTimestamp, null) + ORDER_ASC, params(null, blockTimestamp))

    open fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
    ): List<T> =
        series.rows(
            where(timeFrame, blockTimestamp, null) + ORDER_ASC,
            params(timeFrame, blockTimestamp),
        )

    private fun page(
        timeFrame: TimeFrame?,
        pageable: Pageable,
        after: Long? = null,
        before: Long? = null,
    ): Slice<T> =
        timeFrameSlice(pageable) { direction, offset, limit ->
            series.rows(
                where(timeFrame, after, before) +
                    " ORDER BY block_timestamp ${direction.name}, block_number ${direction.name}" +
                    " OFFSET :offset LIMIT :limit",
                params(timeFrame, after, before)
                    .addValue("offset", offset)
                    .addValue("limit", limit),
            )
        }

    private fun where(timeFrame: TimeFrame?, after: Long?, before: Long?): String =
        "WHERE TRUE" +
            (if (timeFrame == null) ""
            else " AND time_frames @> ARRAY[:frame]::${series.frameType}[]") +
            (if (after == null) "" else " AND block_timestamp > :after") +
            (if (before == null) "" else " AND block_timestamp < :before")

    private fun params(timeFrame: TimeFrame?, after: Long?, before: Long? = null) =
        MapSqlParameterSource("frame", timeFrame?.name)
            .addValue("after", after)
            .addValue("before", before)

    private companion object {
        const val ORDER_ASC = " ORDER BY block_timestamp, block_number"
    }
}
