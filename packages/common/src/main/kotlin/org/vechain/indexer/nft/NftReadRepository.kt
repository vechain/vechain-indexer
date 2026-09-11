package org.vechain.indexer.nft

import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex

/** The API's reads of current ownership, each anti-joined against the blacklist. */
@Repository
@ConditionalOnPostgres
open class NftReadRepository(@Qualifier("postgresJdbcTemplate") jdbcTemplate: JdbcTemplate) {

    private val jdbc = NamedParameterJdbcTemplate(jdbcTemplate)

    open fun findByOwner(
        owner: String,
        excludeContracts: List<String>,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<IndexedNft> =
        nfts(
            "$CURRENT AND n.owner = :owner AND NOT (n.contract_address = ANY(:exclude)) AND $NOT_BLACKLISTED " +
                order(direction),
            MapSqlParameterSource("owner", PostgresHex.bytes(owner))
                .addValue("exclude", excludeContracts.map(PostgresHex::bytes).toTypedArray())
                .addValue("offset", offset)
                .addValue("limit", limit),
        )

    open fun findByOwnerAndContract(
        owner: String,
        contractAddress: String,
        tokenId: String?,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<IndexedNft> =
        nfts(
            "$CURRENT AND n.owner = :owner AND n.contract_address = :contract " +
                (if (tokenId == null) "" else "AND n.token_id = :token ") +
                "AND $NOT_BLACKLISTED " +
                order(direction),
            MapSqlParameterSource("owner", PostgresHex.bytes(owner))
                .addValue("contract", PostgresHex.bytes(contractAddress))
                .addValue("token", tokenId?.let(::BigDecimal))
                .addValue("offset", offset)
                .addValue("limit", limit),
        )

    /** Collections the owner holds, newest acquisition first; Mongo's `$group` had no order. */
    open fun findContractsByOwner(
        owner: String,
        excludeContracts: List<String>,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<String> =
        jdbc.query(
            """
            SELECT n.contract_address FROM nft.ownership n
            WHERE n.superseded_at IS NULL AND n.owner = :owner
              AND NOT (n.contract_address = ANY(:exclude)) AND $NOT_BLACKLISTED
            GROUP BY n.contract_address
            ORDER BY max(n.block_number) ${direction.name}, n.contract_address ${direction.name}
            OFFSET :offset LIMIT :limit
            """
                .trimIndent(),
            MapSqlParameterSource("owner", PostgresHex.bytes(owner))
                .addValue("exclude", excludeContracts.map(PostgresHex::bytes).toTypedArray())
                .addValue("offset", offset)
                .addValue("limit", limit),
        ) { rs, _ ->
            PostgresHex.hex(rs.getBytes(1))
        }

    /** Every current row, blacklisted or not; the e2e suite reads the whole table. */
    open fun findAll(): List<IndexedNft> =
        nfts("$CURRENT ORDER BY n.contract_address, n.token_id", MapSqlParameterSource())

    private fun nfts(sql: String, params: MapSqlParameterSource): List<IndexedNft> =
        jdbc.query(sql, params) { rs, _ -> NftRowMapping.assemble(NftRowMapping.row(rs)) }

    private fun order(direction: Direction) =
        "ORDER BY n.block_number ${direction.name}, n.tx_id ${direction.name}, n.id ${direction.name} " +
            "OFFSET :offset LIMIT :limit"

    companion object {
        private const val CURRENT = "SELECT n.* FROM nft.ownership n WHERE n.superseded_at IS NULL"
        const val NOT_BLACKLISTED =
            "NOT EXISTS (SELECT 1 FROM nft_blacklist.collection_state b WHERE b.contract_address = " +
                "n.contract_address AND b.superseded_at IS NULL AND b.is_blacklisted)"
    }
}
