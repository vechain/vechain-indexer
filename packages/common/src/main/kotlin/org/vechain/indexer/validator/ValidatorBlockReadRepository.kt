package org.vechain.indexer.validator

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex
import org.vechain.indexer.timeseries.TimeSeriesResolution

/** The API's reads of the validator slot ledger. */
@Repository
@ConditionalOnPostgres
open class ValidatorBlockReadRepository(
    @Qualifier("postgresJdbcTemplate") jdbcTemplate: JdbcTemplate
) {

    private val jdbc = NamedParameterJdbcTemplate(jdbcTemplate)

    /** `/block-rewards`: [blockNumber] bounds the page from above (DESC) or below (ASC). */
    open fun findRewards(
        validator: String?,
        blockNumber: Long?,
        status: BlockStatus?,
        direction: Direction,
        offset: Long,
        limit: Int,
    ): List<ValidatorBlock> {
        val bound = if (direction == Direction.ASC) ">=" else "<="
        val dir = direction.name
        return rows(
            "SELECT * FROM validator.slot WHERE TRUE" +
                (if (validator == null) "" else " AND validator = :validator") +
                (if (blockNumber == null) "" else " AND block_number $bound :block") +
                (if (status == null) ""
                else " AND status = CAST(:status AS validator.slot_status)") +
                " ORDER BY block_number $dir, validator, status OFFSET :offset LIMIT :limit",
            MapSqlParameterSource("validator", PostgresHex.bytesOrNull(validator))
                .addValue("block", blockNumber)
                .addValue("status", status?.name)
                .addValue("offset", offset)
                .addValue("limit", limit),
        )
    }

    open fun findByBlockNumber(blockNumber: Long, validator: String?): List<ValidatorBlock> =
        rows(
            "SELECT * FROM validator.slot WHERE block_number = :block" +
                (if (validator == null) "" else " AND validator = :validator") +
                " ORDER BY validator, status",
            MapSqlParameterSource("block", blockNumber)
                .addValue("validator", PostgresHex.bytesOrNull(validator)),
        )

    /** One validator's VALIDATED rows in `[from, to]`, all of them or those [resolution] flags. */
    open fun findValidatedInRange(
        validator: String,
        from: Long,
        to: Long,
        resolution: TimeSeriesResolution,
    ): List<ValidatorBlock> {
        val flag = ValidatorBlockRowMapping.sampleColumn(resolution)
        return rows(
            "SELECT * FROM validator.slot WHERE validator = :validator AND status = 'VALIDATED'" +
                (if (flag == null) "" else " AND $flag") +
                " AND block_timestamp BETWEEN :from AND :to ORDER BY block_timestamp",
            MapSqlParameterSource("validator", PostgresHex.bytes(validator))
                .addValue("from", from)
                .addValue("to", to),
        )
    }

    open fun findLatestValidatedAtOrBefore(validator: String, timestamp: Long): ValidatorBlock? =
        rows(
                "SELECT * FROM validator.slot WHERE validator = :validator AND status = 'VALIDATED'" +
                    " AND block_timestamp <= :at ORDER BY block_timestamp DESC LIMIT 1",
                MapSqlParameterSource("validator", PostgresHex.bytes(validator))
                    .addValue("at", timestamp),
            )
            .firstOrNull()

    /** Slot accounting per validator over `[from, to]`, least uptime first. */
    open fun slotStats(from: Long, to: Long, validator: String? = null): List<ValidatorSlotStats> {
        val inWindow = if (validator == null) "" else " AND validator = :validator"
        val inState = if (validator == null) "" else " AND s.id = :validator"
        return jdbc.query(
            """
            WITH win AS MATERIALIZED (
              SELECT validator, status, block_timestamp FROM validator.slot
              WHERE block_timestamp BETWEEN :from AND :to$inWindow
            ), counts AS (
              SELECT validator, count(*) FILTER (WHERE status = 'VALIDATED') AS proposed,
                     count(*) FILTER (WHERE status = 'MISSED') AS missed
              FROM win GROUP BY validator
            ), misses AS (
              SELECT validator, block_timestamp FROM win WHERE status = 'MISSED'
              UNION ALL
              SELECT s.id, m.block_timestamp FROM validator.state s
              CROSS JOIN LATERAL (
                SELECT block_timestamp FROM validator.slot
                WHERE validator = s.id AND status = 'MISSED' AND block_timestamp < :from
                ORDER BY block_timestamp DESC LIMIT 1
              ) m
              WHERE s.superseded_at IS NULL$inState
            ), offline AS (
              SELECT m.validator, sum(greatest(0,
                       least(nv.block_timestamp, nm.block_timestamp, ex.block_timestamp, :to)
                       - greatest(m.block_timestamp, :from))) AS seconds
              FROM misses m
              LEFT JOIN validator.state s ON s.id = m.validator AND s.superseded_at IS NULL
              LEFT JOIN LATERAL (
                SELECT block_timestamp FROM validator.slot
                WHERE validator = m.validator AND status = 'VALIDATED'
                  AND block_timestamp > m.block_timestamp
                ORDER BY block_timestamp LIMIT 1
              ) nv ON TRUE
              LEFT JOIN LATERAL (
                SELECT block_timestamp FROM validator.slot
                WHERE validator = m.validator AND status = 'MISSED'
                  AND block_timestamp > m.block_timestamp
                ORDER BY block_timestamp LIMIT 1
              ) nm ON TRUE
              LEFT JOIN LATERAL (
                SELECT block_timestamp FROM validator.slot
                WHERE block_number >= s.exit_block ORDER BY block_number LIMIT 1
              ) ex ON TRUE
              GROUP BY m.validator
            )
            SELECT validator, coalesce(c.proposed, 0) AS proposed, coalesce(c.missed, 0) AS missed,
                   coalesce(o.seconds, 0) AS offline_seconds
            FROM counts c FULL JOIN offline o USING (validator)
            WHERE c.validator IS NOT NULL OR o.seconds > 0
            ORDER BY offline_seconds DESC,
                     coalesce(c.missed, 0)::float / nullif(coalesce(c.proposed, 0) + coalesce(c.missed, 0), 0)
                       DESC NULLS LAST,
                     validator
            """
                .trimIndent(),
            MapSqlParameterSource("from", from)
                .addValue("to", to)
                .addValue("validator", PostgresHex.bytesOrNull(validator)),
        ) { rs, _ ->
            val proposed = rs.getLong("proposed")
            val missed = rs.getLong("missed")
            val offline = rs.getLong("offline_seconds")
            ValidatorSlotStats(
                validator = PostgresHex.hex(rs.getBytes("validator")),
                proposedBlocks = proposed,
                missedSlots = missed,
                missedSlotRatio =
                    if (proposed + missed == 0L) 0.0 else missed.toDouble() / (proposed + missed),
                uptimeRatio =
                    if (to > from) (1.0 - offline.toDouble() / (to - from)).coerceIn(0.0, 1.0)
                    else 1.0,
            )
        }
    }

    private fun rows(sql: String, params: MapSqlParameterSource): List<ValidatorBlock> =
        jdbc.query(sql, params) { rs, _ -> ValidatorBlockRowMapping.read(rs) }
}
