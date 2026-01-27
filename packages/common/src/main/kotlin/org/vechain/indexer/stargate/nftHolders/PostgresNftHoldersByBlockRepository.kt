package org.vechain.indexer.stargate.nftHolders

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.model.BlockIdentifier

@Profile("stargate", "nft-holders-by-block")
@Repository
open class PostgresNftHoldersByBlockRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) : NftHoldersByBlockRepository {

    private fun tableName(): String = "nft_holders_by_block"

    private fun mapRow(rs: ResultSet): NftHoldersByBlock {
        val byLevelJson = rs.getString("by_level")
        val byLevelRaw: Map<String, Long> =
            objectMapper.readValue(byLevelJson, object : TypeReference<Map<String, Long>>() {})
        val byLevel = byLevelRaw.mapKeys { TokenLevel.valueOf(it.key) }

        val timeFramesJson = rs.getString("time_frames")
        val timeFrames: List<TimeFrame> =
            objectMapper.readValue(timeFramesJson, object : TypeReference<List<TimeFrame>>() {})

        return NftHoldersByBlock(
            blockNumber = rs.getLong("block_number"),
            blockId = rs.getString("block_id"),
            blockTimestamp = rs.getLong("block_timestamp"),
            total = rs.getLong("total"),
            byLevel = byLevel,
            hourOfDay = rs.getLong("hour_of_day"),
            dayOfMonth = rs.getLong("day_of_month"),
            weekOfYear = rs.getLong("week_of_year"),
            month = rs.getLong("month"),
            year = rs.getLong("year"),
            timeFrames = timeFrames,
            blockTotal = rs.getBigDecimal("block_total")?.toBigInteger(),
            hourTotal = rs.getBigDecimal("hour_total")?.toBigInteger(),
            dayTotal = rs.getBigDecimal("day_total")?.toBigInteger(),
            weekTotal = rs.getBigDecimal("week_total")?.toBigInteger(),
            monthTotal = rs.getBigDecimal("month_total")?.toBigInteger(),
            yearTotal = rs.getBigDecimal("year_total")?.toBigInteger(),
        )
    }

    private fun insertColumns(): String =
        """
        block_number, block_id, block_timestamp, total, by_level, hour_of_day, day_of_month,
        week_of_year, month, year, time_frames, block_total, hour_total, day_total,
        week_total, month_total, year_total
        """
            .trimIndent()

    private fun insertPlaceholders(): String =
        "?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?"

    private fun insertParams(record: NftHoldersByBlock): Array<Any?> =
        arrayOf(
            record.blockNumber,
            record.blockId,
            record.blockTimestamp,
            record.total,
            objectMapper.writeValueAsString(record.byLevel.mapKeys { it.key.name }),
            record.hourOfDay,
            record.dayOfMonth,
            record.weekOfYear,
            record.month,
            record.year,
            objectMapper.writeValueAsString(record.timeFrames),
            record.blockTotal?.let { java.math.BigDecimal(it) },
            record.hourTotal?.let { java.math.BigDecimal(it) },
            record.dayTotal?.let { java.math.BigDecimal(it) },
            record.weekTotal?.let { java.math.BigDecimal(it) },
            record.monthTotal?.let { java.math.BigDecimal(it) },
            record.yearTotal?.let { java.math.BigDecimal(it) },
        )

    @Transactional(rollbackFor = [Exception::class])
    override fun saveAll(records: List<NftHoldersByBlock>) {
        if (records.isEmpty()) return

        jdbcTemplate.batchUpdate(
            """
            INSERT INTO ${tableName()} (${insertColumns()})
            VALUES (${insertPlaceholders()})
            ON CONFLICT (block_number) DO UPDATE SET
                block_id = EXCLUDED.block_id,
                block_timestamp = EXCLUDED.block_timestamp,
                total = EXCLUDED.total,
                by_level = EXCLUDED.by_level,
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
                year_total = EXCLUDED.year_total
            """
                .trimIndent(),
            records.map { insertParams(it) },
        )
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun rollback(blockNumber: Long) {
        jdbcTemplate.update("DELETE FROM ${tableName()} WHERE block_number >= ?", blockNumber)
    }

    override fun getLatestBlockIdentifier(): BlockIdentifier? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT block_number, block_id FROM ${tableName()}
                ORDER BY block_number DESC
                LIMIT 1
                """
                    .trimIndent()
            ) { rs, _ ->
                BlockIdentifier(number = rs.getLong("block_number"), id = rs.getString("block_id"))
            }
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    override fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): NftHoldersByBlock? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM ${tableName()}
                WHERE block_number <= ?
                ORDER BY block_number DESC
                LIMIT 1
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                blockNumber,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    override fun getLatestRecord(): NftHoldersByBlock? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM ${tableName()}
                ORDER BY block_number DESC
                LIMIT 1
                """
                    .trimIndent()
            ) { rs, _ ->
                mapRow(rs)
            }
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    override fun findLatestBeforeOrAtBlockTimestamp(blockTimestamp: Long): NftHoldersByBlock? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM ${tableName()}
                WHERE block_timestamp <= ?
                ORDER BY block_timestamp DESC
                LIMIT 1
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                blockTimestamp,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    private fun queryWithPagination(
        whereClause: String,
        pageable: Pageable,
        vararg params: Any?,
    ): Slice<NftHoldersByBlock> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE $whereClause
                ORDER BY block_timestamp DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                *params,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findByTimeFramesContains(
        timeFrame: TimeFrame,
        pageable: Pageable,
    ): Slice<NftHoldersByBlock> {
        return queryWithPagination("time_frames @> ?::jsonb", pageable, "[\"${timeFrame.name}\"]")
    }

    override fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<NftHoldersByBlock> {
        return queryWithPagination(
            "time_frames @> ?::jsonb AND block_timestamp > ?",
            pageable,
            "[\"${timeFrame.name}\"]",
            blockTimestamp,
        )
    }

    override fun findByBlockTimestampAfter(
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<NftHoldersByBlock> {
        return queryWithPagination("block_timestamp > ?", pageable, blockTimestamp)
    }

    override fun findByTimeFramesContainsAndBlockTimestampBetween(
        timeFrame: TimeFrame,
        from: Long,
        to: Long,
        pageable: Pageable,
    ): Slice<NftHoldersByBlock> {
        return queryWithPagination(
            "time_frames @> ?::jsonb AND block_timestamp >= ? AND block_timestamp <= ?",
            pageable,
            "[\"${timeFrame.name}\"]",
            from,
            to,
        )
    }

    override fun findByTimeFramesContainsAndBlockTimestampBefore(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<NftHoldersByBlock> {
        return queryWithPagination(
            "time_frames @> ?::jsonb AND block_timestamp < ?",
            pageable,
            "[\"${timeFrame.name}\"]",
            blockTimestamp,
        )
    }

    override fun findAll(pageable: Pageable): Slice<NftHoldersByBlock> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                ORDER BY block_timestamp DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findByBlockTimestampBefore(
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<NftHoldersByBlock> {
        return queryWithPagination("block_timestamp < ?", pageable, blockTimestamp)
    }

    override fun findByBlockTimestampBetween(
        from: Long,
        to: Long,
        pageable: Pageable,
    ): Slice<NftHoldersByBlock> {
        return queryWithPagination(
            "block_timestamp >= ? AND block_timestamp <= ?",
            pageable,
            from,
            to,
        )
    }

    override fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
    ): List<NftHoldersByBlock> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE time_frames @> ?::jsonb AND block_timestamp > ?
            ORDER BY block_timestamp DESC
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            "[\"${timeFrame.name}\"]",
            blockTimestamp,
        )
    }

    override fun findByBlockTimestampAfter(blockTimestamp: Long): List<NftHoldersByBlock> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE block_timestamp > ?
            ORDER BY block_timestamp DESC
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            blockTimestamp,
        )
    }
}
