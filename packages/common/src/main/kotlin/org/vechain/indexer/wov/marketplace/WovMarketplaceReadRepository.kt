package org.vechain.indexer.wov.marketplace

import java.math.BigInteger
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex

/** The page behind `/wov/marketplace/buyers`: a buyer's window totals as two seeks per token. */
@Repository
@ConditionalOnPostgres
open class WovMarketplaceReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {

    /** The timestamp of the newest indexed block, or null before the first entry lands. */
    open fun indexedThrough(): Long? =
        jdbc
            .query(
                "SELECT block_timestamp FROM wov_marketplace.progress ORDER BY block_number DESC LIMIT 1"
            ) { rs, _ ->
                rs.getLong(1)
            }
            .firstOrNull()

    /**
     * Up to [limit] buyers after [after] who bought in `[from, to)`, in address order. Each buyer
     * is walked once on the key; each token is a seek to its last row before each boundary.
     */
    open fun buyers(from: Long, to: Long, after: String?, limit: Int): List<WovBuyerWindow> =
        jdbc.query(
            PAGE,
            { rs, _ -> WovBuyerWindow(hex(rs.getBytes("address")), spend(rs.getString("tokens"))) },
            to,
            to,
            from,
            after?.let(::bytes) ?: ByteArray(0),
            to,
            limit,
        )

    private fun spend(packed: String): List<WovTokenSpend> =
        packed.split(',').map {
            val (token, items, spend) = it.split(':')
            WovTokenSpend("0x$token", items.toLong(), BigInteger(spend))
        }

    companion object {
        private const val RUNNING = "wov_marketplace.buyer_running"

        /** The last running row of one token before a boundary; the window's edge for it. */
        private const val EDGE =
            "SELECT r.items, r.spend FROM $RUNNING r WHERE r.buyer = b.address " +
                "AND r.payment_token = t.payment_token AND r.block_timestamp < ? " +
                "ORDER BY r.block_timestamp DESC LIMIT 1"

        internal val PAGE =
            """
            SELECT b.address, s.tokens
            FROM wov_marketplace.buyer b
            CROSS JOIN LATERAL (
                SELECT string_agg(
                    encode(d.payment_token, 'hex') || ':' || d.items::text || ':' || d.spend::text,
                    ',' ORDER BY d.payment_token) AS tokens
                FROM (
                    SELECT t.payment_token,
                           hi.items - COALESCE(lo.items, 0) AS items,
                           hi.spend - COALESCE(lo.spend, 0) AS spend
                    FROM (SELECT r.payment_token FROM $RUNNING r
                          WHERE r.buyer = b.address AND r.block_timestamp < ?
                          GROUP BY r.payment_token) t
                    CROSS JOIN LATERAL ($EDGE) hi
                    LEFT JOIN LATERAL ($EDGE) lo ON true
                ) d
                WHERE d.items > 0
            ) s
            WHERE b.address > ? AND b.first_block_timestamp < ? AND s.tokens IS NOT NULL
            ORDER BY b.address
            LIMIT ?
            """
                .trimIndent()
    }
}
