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
            "SELECT * FROM validator_block.slot WHERE TRUE" +
                (if (validator == null) "" else " AND validator = :validator") +
                (if (blockNumber == null) "" else " AND block_number $bound :block") +
                (if (status == null) ""
                else " AND status = CAST(:status AS validator_block.status)") +
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
            "SELECT * FROM validator_block.slot WHERE block_number = :block" +
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
            "SELECT * FROM validator_block.slot WHERE validator = :validator AND status = 'VALIDATED'" +
                (if (flag == null) "" else " AND $flag") +
                " AND block_timestamp BETWEEN :from AND :to ORDER BY block_timestamp",
            MapSqlParameterSource("validator", PostgresHex.bytes(validator))
                .addValue("from", from)
                .addValue("to", to),
        )
    }

    open fun findLatestValidatedAtOrBefore(validator: String, timestamp: Long): ValidatorBlock? =
        rows(
                "SELECT * FROM validator_block.slot WHERE validator = :validator AND status = 'VALIDATED'" +
                    " AND block_timestamp <= :at ORDER BY block_timestamp DESC LIMIT 1",
                MapSqlParameterSource("validator", PostgresHex.bytes(validator))
                    .addValue("at", timestamp),
            )
            .firstOrNull()

    /**
     * Slot accounting per validator over `[from, to]`, worst ratio first; validators with no row in
     * the window are absent.
     */
    open fun slotStats(from: Long, to: Long, validator: String? = null): List<ValidatorSlotStats> =
        jdbc.query(
            """
            SELECT validator, count(*) FILTER (WHERE status = 'VALIDATED') AS proposed,
                   count(*) FILTER (WHERE status = 'MISSED') AS missed
            FROM validator_block.slot WHERE block_timestamp BETWEEN :from AND :to
            """
                .trimIndent() +
                (if (validator == null) "" else " AND validator = :validator") +
                " GROUP BY validator " +
                "ORDER BY (count(*) FILTER (WHERE status = 'MISSED'))::float / count(*) DESC, validator",
            MapSqlParameterSource("from", from)
                .addValue("to", to)
                .addValue("validator", PostgresHex.bytesOrNull(validator)),
        ) { rs, _ ->
            val proposed = rs.getLong("proposed")
            val missed = rs.getLong("missed")
            ValidatorSlotStats(
                validator = PostgresHex.hex(rs.getBytes("validator")),
                proposedBlocks = proposed,
                missedSlots = missed,
                missedSlotRatio = missed.toDouble() / (proposed + missed),
            )
        }

    private fun rows(sql: String, params: MapSqlParameterSource): List<ValidatorBlock> =
        jdbc.query(sql, params) { rs, _ -> ValidatorBlockRowMapping.read(rs) }
}
