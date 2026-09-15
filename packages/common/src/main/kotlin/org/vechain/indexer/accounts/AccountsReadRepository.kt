package org.vechain.indexer.accounts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex.bytes

/** The current overview behind `/accounts/overview/{address}`. */
@Repository
@ConditionalOnPostgres
open class AccountOverviewReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {
    open fun findByAddress(address: String): AccountOverview? =
        jdbc
            .query(
                "SELECT * FROM ${AccountOverviewRowMapping.TABLE} WHERE address = ? " +
                    "AND superseded_at IS NULL",
                { rs, _ -> AccountOverviewRowMapping.read(rs) },
                bytes(address),
            )
            .firstOrNull()
}

/** The account-count samples behind `/v2/accounts/totals` and `/v2/accounts/total`. */
@Repository
@ConditionalOnPostgres
open class AccountTotalsReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {
    open fun findAllInTimestampRange(from: Long, to: Long): List<AccountTotalsSeries> =
        query("WHERE block_timestamp BETWEEN ? AND ? ORDER BY block_timestamp", from, to)

    /** The rows that opened a [frame] boundary inside the window, oldest first. */
    open fun findFrameInTimestampRange(
        frame: TimeFrame,
        from: Long,
        to: Long,
    ): List<AccountTotalsSeries> =
        query(
            "WHERE time_frames @> ARRAY[?]::${AccountTotalsRowMapping.FRAME_TYPE}[] " +
                "AND block_timestamp BETWEEN ? AND ? ORDER BY block_timestamp",
            frame.name,
            from,
            to,
        )

    /** The newest sample at or before [blockTimestamp], which bookends a window. */
    open fun findLatestAtOrBefore(blockTimestamp: Long): AccountTotalsSeries? =
        query(
                "WHERE block_timestamp <= ? ORDER BY block_timestamp DESC LIMIT 1",
                blockTimestamp,
            )
            .firstOrNull()

    open fun findLatest(): AccountTotalsSeries? =
        query("ORDER BY block_number DESC LIMIT 1").firstOrNull()

    private fun query(where: String, vararg args: Any): List<AccountTotalsSeries> =
        jdbc.query(
            "SELECT * FROM ${AccountTotalsRowMapping.TABLE} $where",
            { rs, _ -> AccountTotalsRowMapping.read(rs) },
            *args,
        )
}

/** The balance history behind `/accounts/balance/vet/{address}`. */
@Repository
@ConditionalOnPostgres
open class VetBalanceReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {
    /** The address's balances inside the window, newest first. */
    open fun findByAddressBetween(address: String, from: Long, to: Long): List<VetBalance> =
        jdbc.query(
            "SELECT * FROM ${VetBalanceRowMapping.TABLE} WHERE address = ? " +
                "AND block_timestamp BETWEEN ? AND ? ORDER BY block_timestamp DESC",
            { rs, _ -> VetBalanceRowMapping.read(rs) },
            bytes(address),
            from,
            to,
        )
}
