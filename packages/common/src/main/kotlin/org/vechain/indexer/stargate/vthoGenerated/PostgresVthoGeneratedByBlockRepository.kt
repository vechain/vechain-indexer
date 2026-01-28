package org.vechain.indexer.stargate.vthoGenerated

import java.math.BigInteger
import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.accounts.TimeFrame

@Profile("stargate", "vtho-generated-by-block")
@Repository
open class PostgresVthoGeneratedByBlockRepository(private val jdbcTemplate: JdbcTemplate) :
    VthoGeneratedByBlockRepository {

    private val tableName = "stargate_vtho_generated_by_block"

    override fun saveAll(records: Iterable<VthoGeneratedByBlock>): Iterable<VthoGeneratedByBlock> {
        val recordsList = records.toList()
        if (recordsList.isEmpty()) return recordsList

        jdbcTemplate.batchUpdate(
            """
            INSERT INTO $tableName (
                block_number, block_id, block_timestamp, hour_of_day, day_of_month,
                week_of_year, month, year, time_frames, block_total, hour_total,
                day_total, week_total, month_total, year_total, total
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::text[], ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (block_number) DO UPDATE SET
                block_id = EXCLUDED.block_id,
                block_timestamp = EXCLUDED.block_timestamp,
                hour_of_day = EXCLUDED.hour_of_day,
                day_of_month = EXCLUDED.day_of_month,
                week_of_year = EXCLUDED.week_of_year,
                month = EXCLUDED.month,
                year = EXCLUDED.year,
                time_frames = EXCLUDED.time_frames,
                block_total = EXCLUDED.block_total,
                hour_total = EXCLUDED.hour_total,
                day_total = EXCLUDED.day_total,
                week_total = EXCLUDED.week_total,
                month_total = EXCLUDED.month_total,
                year_total = EXCLUDED.year_total,
                total = EXCLUDED.total
            """
                .trimIndent(),
            recordsList.map { record ->
                arrayOf(
                    record.blockNumber,
                    record.blockId,
                    record.blockTimestamp,
                    record.hourOfDay,
                    record.dayOfMonth,
                    record.weekOfYear,
                    record.month,
                    record.year,
                    timeFramesToArray(record.timeFrames),
                    record.blockTotal?.toString(),
                    record.hourTotal?.toString(),
                    record.dayTotal?.toString(),
                    record.weekTotal?.toString(),
                    record.monthTotal?.toString(),
                    record.yearTotal?.toString(),
                    record.total.toString(),
                )
            },
        )

        return recordsList
    }

    override fun deleteAllByBlockNumberGreaterThanEqual(start: Long) {
        jdbcTemplate.update("DELETE FROM $tableName WHERE block_number >= ?", start)
    }

    override fun getLatestRecord(): VthoGeneratedByBlock? {
        return try {
            jdbcTemplate.queryForObject(
                "SELECT * FROM $tableName ORDER BY block_number DESC LIMIT 1"
            ) { rs, _ ->
                mapRow(rs)
            }
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    override fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): VthoGeneratedByBlock? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM $tableName 
                WHERE block_number <= ? 
                ORDER BY block_number DESC LIMIT 1
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                blockNumber,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    override fun findLatestBeforeOrAtBlockTimestamp(blockTimestamp: Long): VthoGeneratedByBlock? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM $tableName 
                WHERE block_timestamp <= ? 
                ORDER BY block_timestamp DESC LIMIT 1
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                blockTimestamp,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    override fun findByTimeFramesContains(
        timeFrame: TimeFrame,
        pageable: Pageable,
    ): Slice<VthoGeneratedByBlock> {
        val limit = pageable.pageSize + 1
        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM $tableName 
                WHERE ? = ANY(time_frames)
                ORDER BY block_number DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                timeFrame.name,
                limit,
                pageable.offset,
            )
        return toSlice(results, pageable)
    }

    override fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VthoGeneratedByBlock> {
        val limit = pageable.pageSize + 1
        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM $tableName 
                WHERE ? = ANY(time_frames) AND block_timestamp > ?
                ORDER BY block_number DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                timeFrame.name,
                blockTimestamp,
                limit,
                pageable.offset,
            )
        return toSlice(results, pageable)
    }

    override fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
    ): List<VthoGeneratedByBlock> {
        return jdbcTemplate.query(
            """
            SELECT * FROM $tableName 
            WHERE ? = ANY(time_frames) AND block_timestamp > ?
            ORDER BY block_number DESC
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            timeFrame.name,
            blockTimestamp,
        )
    }

    override fun findByBlockTimestampAfter(
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VthoGeneratedByBlock> {
        val limit = pageable.pageSize + 1
        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM $tableName 
                WHERE block_timestamp > ?
                ORDER BY block_number DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                blockTimestamp,
                limit,
                pageable.offset,
            )
        return toSlice(results, pageable)
    }

    override fun findByBlockTimestampAfter(blockTimestamp: Long): List<VthoGeneratedByBlock> {
        return jdbcTemplate.query(
            """
            SELECT * FROM $tableName 
            WHERE block_timestamp > ?
            ORDER BY block_number DESC
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            blockTimestamp,
        )
    }

    override fun findByTimeFramesContainsAndBlockTimestampBetween(
        timeFrame: TimeFrame,
        from: Long,
        to: Long,
        pageable: Pageable,
    ): Slice<VthoGeneratedByBlock> {
        val limit = pageable.pageSize + 1
        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM $tableName 
                WHERE ? = ANY(time_frames) AND block_timestamp >= ? AND block_timestamp <= ?
                ORDER BY block_number DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                timeFrame.name,
                from,
                to,
                limit,
                pageable.offset,
            )
        return toSlice(results, pageable)
    }

    override fun findByTimeFramesContainsAndBlockTimestampBefore(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VthoGeneratedByBlock> {
        val limit = pageable.pageSize + 1
        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM $tableName 
                WHERE ? = ANY(time_frames) AND block_timestamp < ?
                ORDER BY block_number DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                timeFrame.name,
                blockTimestamp,
                limit,
                pageable.offset,
            )
        return toSlice(results, pageable)
    }

    override fun findAll(pageable: Pageable): Slice<VthoGeneratedByBlock> {
        val limit = pageable.pageSize + 1
        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM $tableName 
                ORDER BY block_number DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                limit,
                pageable.offset,
            )
        return toSlice(results, pageable)
    }

    override fun findByBlockTimestampBefore(
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VthoGeneratedByBlock> {
        val limit = pageable.pageSize + 1
        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM $tableName 
                WHERE block_timestamp < ?
                ORDER BY block_number DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                blockTimestamp,
                limit,
                pageable.offset,
            )
        return toSlice(results, pageable)
    }

    override fun findByBlockTimestampBetween(
        from: Long,
        to: Long,
        pageable: Pageable,
    ): Slice<VthoGeneratedByBlock> {
        val limit = pageable.pageSize + 1
        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM $tableName 
                WHERE block_timestamp >= ? AND block_timestamp <= ?
                ORDER BY block_number DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                from,
                to,
                limit,
                pageable.offset,
            )
        return toSlice(results, pageable)
    }

    private fun mapRow(rs: ResultSet): VthoGeneratedByBlock {
        val timeFramesArray = rs.getArray("time_frames")?.array as? Array<*>
        val timeFrames =
            timeFramesArray?.mapNotNull {
                try {
                    TimeFrame.valueOf(it.toString())
                } catch (_: Exception) {
                    null
                }
            } ?: emptyList()

        return VthoGeneratedByBlock(
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            hourOfDay = rs.getLong("hour_of_day"),
            dayOfMonth = rs.getLong("day_of_month"),
            weekOfYear = rs.getLong("week_of_year"),
            month = rs.getLong("month"),
            year = rs.getLong("year"),
            timeFrames = timeFrames,
            blockTotal = rs.getString("block_total")?.let { BigInteger(it) },
            hourTotal = rs.getString("hour_total")?.let { BigInteger(it) },
            dayTotal = rs.getString("day_total")?.let { BigInteger(it) },
            weekTotal = rs.getString("week_total")?.let { BigInteger(it) },
            monthTotal = rs.getString("month_total")?.let { BigInteger(it) },
            yearTotal = rs.getString("year_total")?.let { BigInteger(it) },
            total = BigInteger(rs.getString("total")),
        )
    }

    private fun timeFramesToArray(timeFrames: List<TimeFrame>): String {
        return "{${timeFrames.joinToString(",") { it.name }}}"
    }

    private fun <T> toSlice(results: List<T>, pageable: Pageable): Slice<T> {
        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results
        return SliceImpl(content, pageable, hasNext)
    }
}
