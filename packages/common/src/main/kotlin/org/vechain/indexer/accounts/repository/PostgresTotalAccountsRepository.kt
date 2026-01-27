package org.vechain.indexer.accounts.repository

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.accounts.TotalAccounts
import org.vechain.indexer.postgres.PostgresVersionedRepository

@Profile("accounts", "total-accounts")
@Repository
open class PostgresTotalAccountsRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<TotalAccounts>(jdbcTemplate, namedJdbcTemplate, objectMapper),
    TotalAccountsRepository {

    override fun tableName(): String = "total_accounts"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        total, time_frame, day_of_month, week_of_year, month, year,
        day_total, week_total, month_total, year_total
        """
            .trimIndent()

    override fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"

    override fun insertParams(doc: TotalAccounts): Array<Any?> =
        arrayOf(
            doc.id,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.total,
            doc.timeFrame?.name,
            doc.dayOfMonth,
            doc.weekOfYear,
            doc.month,
            doc.year,
            doc.dayTotal,
            doc.weekTotal,
            doc.monthTotal,
            doc.yearTotal,
        )

    override fun mapRow(rs: ResultSet): TotalAccounts {
        val timeFrameStr = rs.getString("time_frame")
        val timeFrame = timeFrameStr?.let { TimeFrame.valueOf(it) }

        return TotalAccounts(
            id = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            total = rs.getLongOrNull("total"),
            timeFrame = timeFrame,
            dayOfMonth = rs.getLongOrNull("day_of_month"),
            weekOfYear = rs.getLongOrNull("week_of_year"),
            month = rs.getLongOrNull("month"),
            year = rs.getLongOrNull("year"),
            dayTotal = rs.getLongOrNull("day_total"),
            weekTotal = rs.getLongOrNull("week_total"),
            monthTotal = rs.getLongOrNull("month_total"),
            yearTotal = rs.getLongOrNull("year_total"),
        )
    }

    override fun saveAllVersioned(updated: List<TotalAccounts>, existing: List<TotalAccounts>) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findById(id: String): TotalAccounts? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM ${tableName()}
                WHERE entity_id = ? AND is_current = true
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                id,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    override fun findAllById(ids: Collection<String>): List<TotalAccounts> {
        if (ids.isEmpty()) return emptyList()

        return namedJdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE entity_id IN (:ids) AND is_current = true
            """
                .trimIndent(),
            mapOf("ids" to ids),
        ) { rs, _ ->
            mapRow(rs)
        }
    }

    override fun findByTimeFrameIn(
        timeFrames: List<TimeFrame>,
        pageable: Pageable,
    ): Slice<TotalAccounts> {
        if (timeFrames.isEmpty()) return SliceImpl(emptyList(), pageable, false)

        val limit = pageable.pageSize + 1
        val offset = pageable.offset
        val timeFrameNames = timeFrames.map { it.name }

        val results =
            namedJdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE time_frame IN (:timeFrames) AND is_current = true
                ORDER BY block_timestamp DESC
                LIMIT :limit OFFSET :offset
                """
                    .trimIndent(),
                mapOf("timeFrames" to timeFrameNames, "limit" to limit, "offset" to offset),
            ) { rs, _ ->
                mapRow(rs)
            }

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }
}
