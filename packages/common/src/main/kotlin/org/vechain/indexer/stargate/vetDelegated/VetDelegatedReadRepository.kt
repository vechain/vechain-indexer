package org.vechain.indexer.stargate.vetDelegated

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.stargate.timeFrame.TimeFrameRepo
import org.vechain.indexer.stargate.timeFrame.timeFrameSlice

/**
 * The VET-delegated series behind `/stargate/total-vet-delegated` and `/vet-delegated/{period}`.
 */
@Repository
@ConditionalOnPostgres
open class VetDelegatedReadRepository(
    @Qualifier("postgresJdbcTemplate") jdbcTemplate: JdbcTemplate
) : TimeFrameRepo<VetDelegatedByBlock> {

    private val jdbc = NamedParameterJdbcTemplate(jdbcTemplate)

    override fun getLatestRecord(): VetDelegatedByBlock? =
        rows("$ALL ORDER BY block_number DESC LIMIT 1", MapSqlParameterSource()).firstOrNull()

    override fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): VetDelegatedByBlock? =
        rows(
                "$ALL WHERE block_number <= :block ORDER BY block_number DESC LIMIT 1",
                MapSqlParameterSource("block", blockNumber),
            )
            .firstOrNull()

    override fun findLatestBeforeOrAtBlockTimestamp(timestamp: Long): VetDelegatedByBlock? =
        rows(
                "$ALL WHERE block_timestamp <= :at ORDER BY block_timestamp DESC LIMIT 1",
                MapSqlParameterSource("at", timestamp),
            )
            .firstOrNull()

    override fun findAll(pageable: Pageable): Slice<VetDelegatedByBlock> = page(null, pageable)

    override fun findByTimeFramesContains(
        timeFrame: TimeFrame,
        pageable: Pageable,
    ): Slice<VetDelegatedByBlock> = page(timeFrame, pageable)

    override fun findByBlockTimestampAfter(
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VetDelegatedByBlock> = page(null, pageable, after = blockTimestamp)

    override fun findByBlockTimestampBefore(
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VetDelegatedByBlock> = page(null, pageable, before = blockTimestamp)

    override fun findByBlockTimestampBetween(
        from: Long,
        to: Long,
        pageable: Pageable,
    ): Slice<VetDelegatedByBlock> = page(null, pageable, after = from, before = to)

    override fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VetDelegatedByBlock> = page(timeFrame, pageable, after = blockTimestamp)

    override fun findByTimeFramesContainsAndBlockTimestampBefore(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VetDelegatedByBlock> = page(timeFrame, pageable, before = blockTimestamp)

    override fun findByTimeFramesContainsAndBlockTimestampBetween(
        timeFrame: TimeFrame,
        from: Long,
        to: Long,
        pageable: Pageable,
    ): Slice<VetDelegatedByBlock> = page(timeFrame, pageable, after = from, before = to)

    private fun page(
        timeFrame: TimeFrame?,
        pageable: Pageable,
        after: Long? = null,
        before: Long? = null,
    ): Slice<VetDelegatedByBlock> =
        timeFrameSlice(pageable) { direction, offset, limit ->
            rows(
                ALL +
                    " WHERE TRUE" +
                    (if (timeFrame == null) ""
                    else " AND :frame = ANY(CAST(time_frames AS text[]))") +
                    (if (after == null) "" else " AND block_timestamp > :after") +
                    (if (before == null) "" else " AND block_timestamp < :before") +
                    " ORDER BY block_timestamp ${direction.name}, block_number ${direction.name}" +
                    " OFFSET :offset LIMIT :limit",
                MapSqlParameterSource("frame", timeFrame?.name)
                    .addValue("after", after)
                    .addValue("before", before)
                    .addValue("offset", offset)
                    .addValue("limit", limit),
            )
        }

    private fun rows(sql: String, params: MapSqlParameterSource): List<VetDelegatedByBlock> =
        jdbc.query(sql, params) { rs, _ -> VetDelegatedRowMapping.read(rs) }

    companion object {
        private const val ALL = "SELECT * FROM vet_delegated.total_by_block"
    }
}
