package org.vechain.indexer.validator

import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex

/** Reads of the current delegations, for the API and the indexers under `.dependsOn(...)`. */
@Repository
@ConditionalOnPostgres
open class DelegationReadRepository(@Qualifier("postgresJdbcTemplate") jdbcTemplate: JdbcTemplate) {

    private val jdbc = NamedParameterJdbcTemplate(jdbcTemplate)

    /** `/validators/delegations`: optional validator, token and status filters, paged by block. */
    open fun find(
        validator: String?,
        tokenId: String?,
        statuses: Collection<DelegationStatus>?,
        direction: Direction,
        offset: Long,
        limit: Int,
    ): List<Delegation> =
        query(
            CURRENT +
                (if (validator == null) "" else " AND validator = :validator") +
                (if (tokenId == null) "" else " AND token_id = :token") +
                (if (statuses == null) ""
                else " AND status = ANY(CAST(:statuses AS delegation.status[]))") +
                " ORDER BY block_number ${direction.name}, id ${direction.name} OFFSET :offset LIMIT :limit",
            MapSqlParameterSource("validator", PostgresHex.bytesOrNull(validator))
                .addValue("token", tokenId?.let(::BigDecimal))
                .addValue("statuses", statuses?.map { it.name }?.toTypedArray())
                .addValue("offset", offset)
                .addValue("limit", limit),
        )

    open fun findByValidatorAndStatusIn(
        validator: String,
        statuses: Collection<DelegationStatus>,
    ): List<Delegation> =
        query(
            "$CURRENT AND validator = :validator " +
                "AND status = ANY(CAST(:statuses AS delegation.status[])) ORDER BY id",
            MapSqlParameterSource("validator", PostgresHex.bytes(validator))
                .addValue("statuses", statuses.map { it.name }.toTypedArray()),
        )

    /** Queued, active and exiting counts per validator, or for [validator] alone. */
    open fun countsByValidator(validator: String? = null): List<DelegationStatusCounts> =
        jdbc.query(
            """
            SELECT validator, count(*) FILTER (WHERE status = 'QUEUED') AS queued,
                   count(*) FILTER (WHERE status = 'ACTIVE') AS active,
                   count(*) FILTER (WHERE status = 'EXITING') AS exiting
            FROM delegation.state WHERE superseded_at IS NULL AND status <> 'EXITED'
            """
                .trimIndent() +
                (if (validator == null) "" else " AND validator = :validator") +
                " GROUP BY validator ORDER BY validator",
            MapSqlParameterSource("validator", PostgresHex.bytesOrNull(validator)),
        ) { rs, _ ->
            DelegationStatusCounts(
                validator = PostgresHex.hex(rs.getBytes("validator")),
                queued = rs.getLong("queued"),
                active = rs.getLong("active"),
                exiting = rs.getLong("exiting"),
            )
        }

    /**
     * The non-exited delegations of [validators] as counted `(status, tokenLevel,
     * transitionAtBlock)` buckets, from which the API derives the current- and next-cycle stakes.
     */
    open fun aggregateDelegationFacetsByValidators(
        validators: List<String>
    ): List<DelegationLevelFacet> =
        jdbc.query(
            """
            SELECT validator, status, token_level, transition_at_block, count(*) AS count
            FROM delegation.state WHERE superseded_at IS NULL AND status <> 'EXITED'
              AND validator = ANY(:validators)
            GROUP BY validator, status, token_level, transition_at_block
            ORDER BY validator, status, token_level, transition_at_block
            """
                .trimIndent(),
            MapSqlParameterSource("validators", validators.map(PostgresHex::bytes).toTypedArray()),
        ) { rs, _ ->
            DelegationLevelFacet(
                validator = PostgresHex.hex(rs.getBytes("validator")),
                status = rs.getString("status"),
                tokenLevel = rs.getString("token_level"),
                transitionAtBlock = rs.getObject("transition_at_block", Long::class.javaObjectType),
                count = rs.getLong("count"),
            )
        }

    private fun query(sql: String, params: MapSqlParameterSource): List<Delegation> =
        jdbc.query(sql, params) { rs, _ -> DelegationRowMapping.read(rs) }

    companion object {
        private const val CURRENT = "SELECT * FROM delegation.state WHERE superseded_at IS NULL"
    }
}
