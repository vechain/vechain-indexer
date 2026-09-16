package org.vechain.indexer.b3tr.balance

import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.b3tr.balance.B3trBalanceRowMapping.TABLE
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex.bytes

/**
 * The balance a richlist page or rank is measured on; names the column, so no string reaches SQL.
 */
enum class B3trBalanceColumn(val column: String) {
    TOTAL("total_balance"),
    VOT3("vot3_balance"),
    B3TR("b3tr_balance"),
}

/** The keyset page and the rank counts behind `/b3tr/richlist`. */
@Repository
@ConditionalOnPostgres
open class B3trBalanceReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {

    open fun findByAddress(address: String): B3trBalance? =
        jdbc
            .query(
                "SELECT * FROM $TABLE WHERE address = ? AND superseded_at IS NULL",
                { rs, _ -> B3trBalanceRowMapping.read(rs) },
                bytes(address),
            )
            .firstOrNull()

    /**
     * The holders after the cursor, in [direction] of [on] with the address breaking ties ascending
     * either way, as the Mongo keyset did.
     */
    open fun page(
        on: B3trBalanceColumn,
        limit: Int,
        direction: Direction,
        cursorBalance: BigDecimal?,
        cursorAddress: String?,
    ): List<B3trBalance> {
        val col = on.column
        val args = mutableListOf<Any>()
        var keyset = ""
        if (cursorBalance != null && cursorAddress != null) {
            val past = if (direction == Direction.DESC) "<" else ">"
            keyset = "AND ($col $past ? OR ($col = ? AND address > ?)) "
            args += cursorBalance
            args += cursorBalance
            args += bytes(cursorAddress)
        }
        args += limit
        return jdbc.query(
            "SELECT * FROM $TABLE WHERE superseded_at IS NULL AND $col > 0 $keyset" +
                "ORDER BY $col ${direction.name}, address LIMIT ?",
            { rs, _ -> B3trBalanceRowMapping.read(rs) },
            *args.toTypedArray(),
        )
    }

    open fun countGreaterThan(on: B3trBalanceColumn, threshold: BigDecimal): Long =
        jdbc.queryForObject(
            // The redundant `> 0` is what lets the planner use the holders-only index.
            "SELECT count(*) FROM $TABLE WHERE superseded_at IS NULL AND ${on.column} > 0 " +
                "AND ${on.column} > ?",
            Long::class.java,
            threshold,
        )!!

    /** The newest indexed block's timestamp, or null while empty; the cache warmer reads it. */
    open fun newestBlockTimestamp(): Long? =
        jdbc
            .query(
                "SELECT block_timestamp FROM $TABLE ORDER BY block_number DESC LIMIT 1",
                { rs, _ -> rs.getLong(1) },
            )
            .firstOrNull()
}
