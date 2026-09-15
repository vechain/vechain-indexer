package org.vechain.indexer.b3tr.navigator

import java.math.BigInteger
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex.bytes

data class NavigatorOverview(
    val activeNavigators: Long,
    val totalStaked: BigInteger,
    val totalCitizens: Long,
    val totalDelegated: BigInteger,
)

data class NavigatorFeeSummary(val totalEarned: BigInteger, val totalClaimed: BigInteger)

/** The fields `/navigators` can be ordered by, as the request names them and as the row does. */
enum class NavigatorSort(val property: String, val column: String) {
    STAKE("stake", "stake"),
    TOTAL_DELEGATED("totalDelegated", "total_delegated"),
    CITIZEN_COUNT("citizenCount", "citizen_count"),
    REGISTERED_AT("registeredAt", "registered_at");

    companion object {
        fun parse(raw: String?): NavigatorSort =
            entries.firstOrNull { it.property.equals(raw, ignoreCase = true) } ?: REGISTERED_AT
    }
}

/** The current rows behind the `/b3tr/navigators` endpoints, each read in one query. */
@Repository
@ConditionalOnPostgres
open class NavigatorReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {

    /**
     * The navigators, optionally narrowed to some statuses, in one order with a stable tiebreak.
     */
    open fun findNavigators(
        statuses: Collection<NavigatorStatus>?,
        sort: NavigatorSort,
        direction: Direction,
        offset: Long,
        limit: Int,
    ): List<Navigator> {
        val filter =
            if (statuses == null) ""
            else "AND status = ANY(CAST(? AS ${NavigatorRowMapping.STATUS_TYPE}[])) "
        val args = listOfNotNull(statuses?.map { it.name }?.toTypedArray(), offset, limit)
        return jdbc.query(
            "SELECT * FROM $NAVIGATOR_TABLE WHERE superseded_at IS NULL $filter" +
                "ORDER BY ${sort.column} ${direction.name}, address ASC OFFSET ? LIMIT ?",
            { rs, _ -> NavigatorRowMapping.read(rs) },
            *args.toTypedArray(),
        )
    }

    open fun findNavigator(address: String): Navigator? =
        jdbc
            .query(
                "SELECT * FROM $NAVIGATOR_TABLE WHERE address = ? AND superseded_at IS NULL",
                { rs, _ -> NavigatorRowMapping.read(rs) },
                bytes(address),
            )
            .firstOrNull()

    /** Every navigator that is not deactivated, summed: exiting ones count until the deadline. */
    open fun overview(): NavigatorOverview =
        jdbc.queryForObject(
            "SELECT count(*) AS active_navigators, coalesce(sum(stake), 0) AS total_staked, " +
                "coalesce(sum(citizen_count), 0) AS total_citizens, " +
                "coalesce(sum(total_delegated), 0) AS total_delegated " +
                "FROM $NAVIGATOR_TABLE WHERE superseded_at IS NULL AND status <> 'DEACTIVATED'"
        ) { rs, _ ->
            NavigatorOverview(
                activeNavigators = rs.getLong("active_navigators"),
                totalStaked = rs.getBigDecimal("total_staked").toBigIntegerExact(),
                totalCitizens = rs.getLong("total_citizens"),
                totalDelegated = rs.getBigDecimal("total_delegated").toBigIntegerExact(),
            )
        }!!

    /** A navigator's, a citizen's, or the pair's delegation events, paged by time. */
    open fun findDelegationEvents(
        navigator: String?,
        citizen: String?,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<NavigatorDelegationEvent> {
        val order = direction.name
        val filters =
            listOfNotNull(navigator?.let { "navigator = ?" }, citizen?.let { "citizen = ?" })
        val args = listOfNotNull(navigator?.let(::bytes), citizen?.let(::bytes), offset, limit)
        return jdbc.query(
            "SELECT * FROM $DELEGATION_EVENT_TABLE " +
                (if (filters.isEmpty()) "" else filters.joinToString(" AND ", prefix = "WHERE ")) +
                " ORDER BY block_timestamp $order, tx_id $order, id $order OFFSET ? LIMIT ?",
            { rs, _ -> NavigatorDelegationEventRowMapping.read(rs) },
            *args.toTypedArray(),
        )
    }

    /** The citizens delegating to [navigator], paged by when the delegation began. */
    open fun findCitizens(
        navigator: String,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<NavigatorCitizen> {
        val order = direction.name
        return jdbc.query(
            "SELECT * FROM $CITIZEN_TABLE WHERE navigator = ? AND superseded_at IS NULL AND active " +
                "ORDER BY delegated_at $order, address $order OFFSET ? LIMIT ?",
            { rs, _ -> NavigatorCitizenRowMapping.read(rs) },
            bytes(navigator),
            offset,
            limit,
        )
    }

    /** What one navigator, or all of them, has been paid and has claimed. */
    open fun feeSummary(navigator: String?): NavigatorFeeSummary =
        jdbc.queryForObject(
            "SELECT coalesce(sum(total_deposited), 0) AS total_earned, " +
                "coalesce(sum(claimed_amount), 0) AS total_claimed " +
                "FROM $FEE_TABLE WHERE superseded_at IS NULL" +
                (if (navigator == null) "" else " AND navigator = ?"),
            { rs, _ ->
                NavigatorFeeSummary(
                    totalEarned = rs.getBigDecimal("total_earned").toBigIntegerExact(),
                    totalClaimed = rs.getBigDecimal("total_claimed").toBigIntegerExact(),
                )
            },
            *listOfNotNull(navigator?.let(::bytes)).toTypedArray(),
        )!!

    /** A navigator's fees, one row per round, paged by round. */
    open fun findFees(
        navigator: String,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<NavigatorFee> =
        jdbc.query(
            "SELECT * FROM $FEE_TABLE WHERE navigator = ? AND superseded_at IS NULL " +
                "ORDER BY round_id ${direction.name} OFFSET ? LIMIT ?",
            { rs, _ -> NavigatorFeeRowMapping.read(rs) },
            bytes(navigator),
            offset,
            limit,
        )

    /** The newest block any of the four tables has seen. */
    open fun latestBlockNumber(): Long =
        jdbc.queryForObject(
            listOf(NAVIGATOR_TABLE, CITIZEN_TABLE, DELEGATION_EVENT_TABLE, FEE_TABLE)
                .joinToString(
                    prefix = "SELECT GREATEST(",
                    postfix = ")",
                    transform = { "(SELECT coalesce(max(block_number), 0) FROM $it)" },
                ),
            Long::class.java,
        )!!

    companion object {
        private const val NAVIGATOR_TABLE = NavigatorRowMapping.TABLE
        private const val CITIZEN_TABLE = NavigatorCitizenRowMapping.TABLE
        private const val DELEGATION_EVENT_TABLE = NavigatorDelegationEventRowMapping.TABLE
        private const val FEE_TABLE = NavigatorFeeRowMapping.TABLE
    }
}
