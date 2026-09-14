package org.vechain.indexer.timeseries

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

/** One series table: rows upserted by block, read newest first, rolled back by block. */
class TimeFrameTable<T : TimeFrameDocument>(
    private val jdbc: JdbcTemplate,
    private val mapping: TimeFrameRowMapping<T>,
) {
    private val named = NamedParameterJdbcTemplate(jdbc)
    val table = mapping.table

    /** The schema's own enum, as every series migration declares it. */
    val frameType = "${table.substringBefore('.')}.time_frame"

    private val columns = TimeFrameColumns.COLUMNS + mapping.columns
    private val insert =
        "INSERT INTO $table (block_number, ${columns.joinToString()}) " +
            "VALUES (?, ${columns.joinToString { "?" }}) ON CONFLICT (block_number) DO UPDATE SET " +
            columns.joinToString { "$it = EXCLUDED.$it" }

    fun save(records: List<T>) {
        if (records.isEmpty()) return
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = records.associateBy { it.blockNumber }.values.toList()
        jdbc.batchUpdate(insert, rows, rows.size) { ps, d ->
            mapping.bind(ps, TimeFrameColumns.bind(ps, d, frameType), d)
        }
    }

    fun latest(): T? = rows("ORDER BY block_number DESC LIMIT 1").firstOrNull()

    fun rollbackFrom(blockNumber: Long) {
        jdbc.update("DELETE FROM $table WHERE block_number >= ?", blockNumber)
    }

    fun truncate() {
        jdbc.execute("TRUNCATE $table")
    }

    /** `SELECT *` with [clause] appended, its named parameters taken from [params]. */
    fun rows(clause: String, params: MapSqlParameterSource = MapSqlParameterSource()): List<T> =
        named.query("SELECT * FROM $table $clause", params) { rs, _ -> mapping.read(rs) }
}
