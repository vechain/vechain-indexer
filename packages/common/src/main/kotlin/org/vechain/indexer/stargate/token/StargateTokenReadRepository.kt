package org.vechain.indexer.stargate.token

import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex
import org.vechain.indexer.thor.Address

/** The API's reads of the current token snapshots. */
@Repository
@ConditionalOnPostgres
open class StargateTokenReadRepository(
    @Qualifier("postgresJdbcTemplate") jdbcTemplate: JdbcTemplate
) {

    private val jdbc = NamedParameterJdbcTemplate(jdbcTemplate)

    open fun findById(tokenId: String): StargateToken? =
        query("$CURRENT AND token_id = :token", MapSqlParameterSource("token", BigDecimal(tokenId)))
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
        return query(
            "$CURRENT AND owner <> :zero$scope ORDER BY block_number ${direction.name}, " +
                "token_id ${direction.name} OFFSET :offset LIMIT :limit",
            MapSqlParameterSource("zero", PostgresHex.bytes(Address.ZERO_ADDRESS))
                .addValue("owner", PostgresHex.bytesOrNull(owner))
                .addValue("manager", PostgresHex.bytesOrNull(manager))
                .addValue("offset", offset)
                .addValue("limit", limit),
        )
    }

    private fun query(sql: String, params: MapSqlParameterSource): List<StargateToken> =
        jdbc.query(sql, params) { rs, _ -> StargateTokenRowMapping.read(rs) }

    companion object {
        private const val CURRENT = "SELECT * FROM stargate_token.state WHERE superseded_at IS NULL"
    }
}
