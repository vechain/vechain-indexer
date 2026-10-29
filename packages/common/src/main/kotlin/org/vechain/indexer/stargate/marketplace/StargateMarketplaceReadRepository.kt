package org.vechain.indexer.stargate.marketplace

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
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.Address

/** The API's reads of open listings, each joined to its token's current `stargate_token` row. */
@Repository
@ConditionalOnPostgres
open class StargateMarketplaceReadRepository(
    @Qualifier("postgresJdbcTemplate") jdbcTemplate: JdbcTemplate
) {

    private val jdbc = NamedParameterJdbcTemplate(jdbcTemplate)

    /**
     * Open VET listings whose token is still held by its seller, narrowed to [level] and [seller]
     * when given; the sale id breaks ties so a page is stable.
     */
    open fun findValid(
        level: TokenLevel?,
        seller: String?,
        sort: ListingSort,
        direction: Direction,
        offset: Long,
        limit: Int,
    ): List<ListedToken> {
        val scope = buildString {
            if (level != null) append(" AND t.level = :level")
            if (seller != null) append(" AND l.seller = :seller")
        }
        val column =
            when (sort) {
                ListingSort.NEWEST -> "l.block_number"
                ListingSort.PRICE -> "l.price"
            }
        val order = "ORDER BY $column ${direction.name}, l.sale_id ${direction.name}"
        return jdbc.query(
            "$VALID$scope $order OFFSET :offset LIMIT :limit",
            MapSqlParameterSource("zero", PostgresHex.bytes(Address.ZERO_ADDRESS))
                .addValue("level", level?.name)
                .addValue("seller", PostgresHex.bytesOrNull(seller))
                .addValue("offset", offset)
                .addValue("limit", limit),
        ) { rs, _ ->
            read(rs)
        }
    }

    /** The token's open listing with whatever state the token has now, or null if not listed. */
    open fun findOpenByToken(tokenId: String): ListedToken? =
        jdbc
            .query(OPEN_BY_TOKEN, MapSqlParameterSource("token", BigDecimal(tokenId))) { rs, _ ->
                read(rs)
            }
            .firstOrNull()

    private fun read(rs: ResultSet): ListedToken =
        ListedToken(
            listing = StargateListingRowMapping.read(rs),
            level = rs.getString("level")?.let(TokenLevel::valueOf),
            vetStaked = rs.getBigDecimal("vet_staked")?.toBigIntegerExact(),
            owner = PostgresHex.hexOrNull(rs.getBytes("owner")),
        )

    companion object {
        private const val SELECT =
            "SELECT l.*, t.level, t.vet_staked, t.owner FROM ${StargateListingRowMapping.TABLE} l"
        private const val TOKEN =
            "stargate_token.state t ON t.token_id = l.token_id AND t.superseded_at IS NULL"
        private const val OPEN = "l.superseded_at IS NULL AND l.status = 'OPEN'"
        private const val VALID =
            "$SELECT JOIN $TOKEN WHERE $OPEN AND l.payment_token = :zero " +
                "AND t.owner = l.seller AND t.owner <> :zero"
        private const val OPEN_BY_TOKEN =
            "$SELECT LEFT JOIN $TOKEN WHERE $OPEN AND l.token_id = :token " +
                "ORDER BY l.block_number DESC LIMIT 1"
    }
}
