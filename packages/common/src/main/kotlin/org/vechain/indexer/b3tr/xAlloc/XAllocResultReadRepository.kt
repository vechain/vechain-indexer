package org.vechain.indexer.b3tr.xAlloc

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.b3tr.xAlloc.XAllocResultRowMapping.TABLE
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex.bytes

/** The current round and app results behind the `/b3tr/x-alloc` endpoints. */
@Repository
@ConditionalOnPostgres
open class XAllocResultReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {

    open fun findByAppIdAndRoundId(appId: String, roundId: Int): XAllocResult? =
        query("$CURRENT AND app_id = ? AND round_id = ?", bytes(appId), roundId).firstOrNull()

    /** A round's apps, most voted first. */
    open fun findByRoundId(roundId: Int): List<XAllocResult> =
        query("$CURRENT AND round_id = ? ORDER BY votes_received DESC, app_id", roundId)

    /** A round's apps that have claimed, largest earner first. */
    open fun findEarningsByRoundId(roundId: Int): List<XAllocResult> =
        query(
            "$CURRENT AND round_id = ? AND total_amount IS NOT NULL " +
                "ORDER BY total_amount DESC, app_id",
            roundId,
        )

    /** One app's earnings round by round. */
    open fun findEarningsByAppId(appId: String): List<XAllocResult> =
        query(
            "$CURRENT AND app_id = ? AND total_amount IS NOT NULL " +
                "ORDER BY round_id, total_amount DESC",
            bytes(appId),
        )

    open fun latestBlockNumber(): Long =
        jdbc.queryForObject("SELECT coalesce(max(block_number), 0) FROM $TABLE", Long::class.java)!!

    private fun query(sql: String, vararg args: Any): List<XAllocResult> =
        jdbc.query(sql, { rs, _ -> XAllocResultRowMapping.read(rs) }, *args)

    companion object {
        private const val CURRENT = "SELECT * FROM $TABLE WHERE superseded_at IS NULL"
    }
}
