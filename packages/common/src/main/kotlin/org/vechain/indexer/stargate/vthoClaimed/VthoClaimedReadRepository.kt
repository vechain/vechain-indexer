package org.vechain.indexer.stargate.vthoClaimed

import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.timeseries.TimeFrameReadRepository

/** The VTHO-claimed series and the per-account totals behind `/stargate/total-vtho-claimed`. */
@Repository
@ConditionalOnPostgres
open class VthoClaimedReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : TimeFrameReadRepository<VthoClaimedByBlock>(jdbc, VthoClaimedRowMapping) {

    /**
     * [account]'s claims summed over its tokens, or over [tokenId] alone; null if it never claimed.
     */
    open fun findByAccount(account: String, tokenId: String? = null): VthoClaimedTotals? =
        jdbc
            .query(
                "SELECT SUM(legacy_rewards), SUM(delegation_rewards) FROM " +
                    "${VthoClaimedByTokenRowMapping.TABLE} WHERE account = ? AND superseded_at IS NULL" +
                    (if (tokenId == null) "" else " AND token_id = ?"),
                { rs, _ ->
                    rs.getBigDecimal(1)?.let {
                        VthoClaimedTotals(
                            it.toBigIntegerExact(),
                            rs.getBigDecimal(2).toBigIntegerExact(),
                        )
                    }
                },
                *listOfNotNull(bytes(account), tokenId?.let(::BigDecimal)).toTypedArray(),
            )
            .firstOrNull()
}
