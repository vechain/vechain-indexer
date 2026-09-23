package org.vechain.indexer.stargate.token

import java.math.BigDecimal
import java.sql.ResultSet
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex
import org.vechain.indexer.thor.Address
import org.vechain.indexer.validator.DelegationStatus
import org.vechain.indexer.validator.Status

/** The API's reads of the current token snapshots, each with its latest delegation. */
@Repository
@ConditionalOnPostgres
open class StargateTokenReadRepository(
    @Qualifier("postgresJdbcTemplate") jdbcTemplate: JdbcTemplate
) {

    private val jdbc = NamedParameterJdbcTemplate(jdbcTemplate)

    open fun findById(tokenId: String): StargateToken? =
        query(
                "$CURRENT AND token_id = :token",
                "",
                MapSqlParameterSource("token", BigDecimal(tokenId)),
            )
            .firstOrNull()

    /**
     * Unburned tokens held by [owner], managed by [manager], either when both are given, or all of
     * them; paged by block with the token id as tiebreak.
     */
    open fun findActive(
        owner: String?,
        manager: String?,
        direction: Direction,
        offset: Long,
        limit: Int,
    ): List<StargateToken> {
        val scope =
            when {
                owner != null && manager != null -> " AND (owner = :owner OR manager = :manager)"
                owner != null -> " AND owner = :owner"
                manager != null -> " AND manager = :manager"
                else -> ""
            }
        val order = "ORDER BY block_number ${direction.name}, token_id ${direction.name}"
        return query(
            "$CURRENT AND owner <> :zero$scope $order OFFSET :offset LIMIT :limit",
            order,
            MapSqlParameterSource("zero", PostgresHex.bytes(Address.ZERO_ADDRESS))
                .addValue("owner", PostgresHex.bytesOrNull(owner))
                .addValue("manager", PostgresHex.bytesOrNull(manager))
                .addValue("offset", offset)
                .addValue("limit", limit),
        )
    }

    // The join runs on the page alone, so [order] is applied again outside it.
    private fun query(
        page: String,
        order: String,
        params: MapSqlParameterSource,
    ): List<StargateToken> =
        jdbc.query("$WITH_DELEGATION ($page) t $LATEST_DELEGATION $order", params) { rs, _ ->
            read(rs)
        }

    // An exited delegation reads as NONE, as the token's own rows did before.
    private fun read(rs: ResultSet): StargateToken {
        val token = StargateTokenRowMapping.read(rs)
        val status = rs.getString("delegation_status")?.let(DelegationStatus::valueOf)
        if (status == null || status == DelegationStatus.EXITED) return token
        return token.copy(
            delegationStatus = Status.valueOf(status.name),
            validatorId = PostgresHex.hex(rs.getBytes("delegation_validator")),
        )
    }

    companion object {
        private const val CURRENT = "SELECT * FROM stargate_token.state WHERE superseded_at IS NULL"
        private const val WITH_DELEGATION =
            "SELECT t.*, d.status AS delegation_status, d.validator AS delegation_validator FROM"
        // Staker delegation ids only grow, so a token's highest id is its latest delegation.
        private const val LATEST_DELEGATION =
            "LEFT JOIN LATERAL (SELECT status, validator FROM delegation.state " +
                "WHERE token_id = t.token_id AND superseded_at IS NULL ORDER BY id DESC LIMIT 1) d " +
                "ON TRUE"
    }
}
