package org.vechain.indexer.explorer

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.config.postgres.ConditionalOnPostgres

/** The block-usage samples behind `/explorer/block-usage`. */
@Repository
@ConditionalOnPostgres
open class BlockUsageReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {

    open fun findAllInTimestampRange(from: Long, to: Long): List<BlockUsage> =
        query("WHERE block_timestamp BETWEEN ? AND ? ORDER BY block_timestamp", from, to)

    /** The blocks that opened a [frame] boundary inside the window, oldest first. */
    open fun findFrameInTimestampRange(
        frame: TimeFrame,
        from: Long,
        to: Long,
    ): List<BlockUsage> =
        query(
            "WHERE time_frames @> ARRAY[?]::${BlockUsageRowMapping.FRAME_TYPE}[] " +
                "AND block_timestamp BETWEEN ? AND ? ORDER BY block_timestamp",
            frame.name,
            from,
            to,
        )

    /** The newest sample at or before [blockTimestamp], which bookends a window. */
    open fun findLatestAtOrBefore(blockTimestamp: Long): BlockUsage? =
        query(
                "WHERE block_timestamp <= ? ORDER BY block_timestamp DESC LIMIT 1",
                blockTimestamp,
            )
            .firstOrNull()

    private fun query(where: String, vararg args: Any): List<BlockUsage> =
        jdbc.query(
            "SELECT * FROM ${BlockUsageRowMapping.TABLE} $where",
            { rs, _ -> BlockUsageRowMapping.read(rs) },
            *args,
        )
}

/** The daily rollups behind `/explorer/average-fees-per-user`. */
@Repository
@ConditionalOnPostgres
open class AverageFeesPerUserReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {

    open fun findBetween(fromDayStart: Long, toDayStart: Long): List<AverageFeesPerUser> =
        jdbc.query(
            "SELECT * FROM ${AverageFeesPerUserRowMapping.TABLE} WHERE superseded_at IS NULL " +
                "AND day_start_timestamp BETWEEN ? AND ? ORDER BY day_start_timestamp",
            { rs, _ -> AverageFeesPerUserRowMapping.read(rs) },
            fromDayStart,
            toDayStart,
        )
}
