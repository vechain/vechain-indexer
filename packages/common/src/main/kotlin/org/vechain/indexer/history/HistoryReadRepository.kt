package org.vechain.indexer.history

import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex

/**
 * The API's history reads: one statement per page, flagged NFT transfers dropped by an anti-join.
 */
@Repository
@ConditionalOnPostgres
open class HistoryReadRepository(@Qualifier("postgresJdbcTemplate") jdbcTemplate: JdbcTemplate) {

    private val jdbc = NamedParameterJdbcTemplate(jdbcTemplate)

    /** `searchBy` values to the column they name. */
    enum class SearchField(val column: String) {
        TO("to_address"),
        FROM("from_address"),
        ORIGIN("origin"),
        GAS_PAYER("gas_payer");

        companion object {
            fun of(name: String): SearchField? = entries.firstOrNull {
                it.name.replace("_", "").equals(name, ignoreCase = true)
            }
        }
    }

    /** `/history/{account}`: every event the account took part in, through the address table. */
    open fun findByAccount(
        account: String,
        eventNames: List<String>?,
        contractAddress: String?,
        after: Long?,
        before: Long?,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<IndexedHistoryEvent> {
        val names = known(eventNames)
        if (names != null && names.isEmpty()) return emptyList()
        val where =
            listOfNotNull(
                "a.address = :account",
                names?.let { "a.event_name = ANY(CAST(:names AS history.event_name[]))" },
                after?.let { "a.block_timestamp >= :after" },
                before?.let { "a.block_timestamp <= :before" },
                contractAddress?.let { "e.contract_address = :contract" },
                NOT_BLACKLISTED_TRANSFER,
            )
        return events(
            """
            SELECT e.* FROM history.event_address a JOIN history.event e ON e.id = a.event_id
            WHERE ${where.joinToString(" AND ")}
            ORDER BY a.block_timestamp ${direction.name}, a.event_id ${direction.name} OFFSET :offset LIMIT :limit
            """
                .trimIndent(),
            params(names, after, before, offset, limit)
                .addValue("account", PostgresHex.bytes(account))
                .addValue("contract", contractAddress?.let(PostgresHex::bytes)),
        )
    }

    /** `/history/{account}?searchBy=`: the union of one indexed scan per named field. */
    open fun findBySearchFields(
        account: String,
        fields: List<SearchField>,
        eventNames: List<String>?,
        contractAddress: String?,
        after: Long?,
        before: Long?,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<IndexedHistoryEvent> {
        val names = known(eventNames)
        if (names != null && names.isEmpty()) return emptyList()
        if (fields.isEmpty()) return emptyList()
        val filters =
            listOfNotNull(
                names?.let { "e.event_name = ANY(CAST(:names AS history.event_name[]))" },
                after?.let { "e.block_timestamp >= :after" },
                before?.let { "e.block_timestamp <= :before" },
                contractAddress?.let { "e.contract_address = :contract" },
                NOT_BLACKLISTED_TRANSFER,
            )
        val order = "block_timestamp ${direction.name}, id ${direction.name}"
        val branches =
            fields.distinct().joinToString(" UNION ") { field ->
                "(SELECT e.id, e.block_timestamp FROM history.event e " +
                    "WHERE e.${field.column} = :account AND ${filters.joinToString(" AND ")} " +
                    "ORDER BY e.$order LIMIT :reach)"
            }
        return events(
            """
            WITH page AS (SELECT id, block_timestamp FROM ($branches) u
                          ORDER BY $order OFFSET :offset LIMIT :limit)
            SELECT e.* FROM page p JOIN history.event e ON e.id = p.id ORDER BY p.$order
            """
                .trimIndent(),
            params(names, after, before, offset, limit)
                .addValue("account", PostgresHex.bytes(account))
                .addValue("contract", contractAddress?.let(PostgresHex::bytes))
                .addValue("reach", offset + limit),
        )
    }

    /** `/nfts/history`: one token's transfers and sales. */
    open fun findTokenHistory(
        contractAddress: String,
        tokenId: String,
        eventNames: List<String>,
        after: Long?,
        before: Long?,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<IndexedHistoryEvent> {
        val names = known(eventNames)
        if (names != null && names.isEmpty()) return emptyList()
        val where =
            listOfNotNull(
                "e.contract_address = :contract",
                "e.token_id = :token",
                "e.event_name = ANY(CAST(:names AS history.event_name[]))",
                after?.let { "e.block_timestamp >= :after" },
                before?.let { "e.block_timestamp <= :before" },
                NOT_BLACKLISTED_TRANSFER,
            )
        return events(
            "$SELECT WHERE ${where.joinToString(" AND ")} ${order(direction)}",
            params(names, after, before, offset, limit)
                .addValue("contract", PostgresHex.bytes(contractAddress))
                .addValue("token", BigDecimal(tokenId)),
        )
    }

    /**
     * `/stargate/tokens/{id}/history`: protocol events, plus NFT events on the Stargate contract.
     */
    open fun findStargateTokenHistory(
        tokenId: String,
        eventNames: List<String>?,
        protocolEvents: List<String>,
        nftEvents: List<String>,
        stargateContract: String,
        after: Long?,
        before: Long?,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<IndexedHistoryEvent> {
        val names = known(eventNames)
        if (names != null && names.isEmpty()) return emptyList()
        val where =
            listOfNotNull(
                "e.token_id = :token",
                names?.let { "e.event_name = ANY(CAST(:names AS history.event_name[]))" },
                "(e.event_name = ANY(CAST(:protocol AS history.event_name[])) OR " +
                    "(e.event_name = ANY(CAST(:nft AS history.event_name[])) AND e.contract_address = :stargate))",
                after?.let { "e.block_timestamp >= :after" },
                before?.let { "e.block_timestamp <= :before" },
                NOT_BLACKLISTED_TRANSFER,
            )
        return events(
            "$SELECT WHERE ${where.joinToString(" AND ")} ${order(direction)}",
            params(names, after, before, offset, limit)
                .addValue("token", BigDecimal(tokenId))
                .addValue("protocol", protocolEvents.toTypedArray())
                .addValue("nft", nftEvents.toTypedArray())
                .addValue("stargate", PostgresHex.bytes(stargateContract)),
        )
    }

    /**
     * `/b3tr/actions`: B3TR_ACTION rows; a single-sided window is exclusive, as the Mongo one was.
     */
    open fun findActions(
        to: String?,
        appId: String?,
        after: Long?,
        before: Long?,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<IndexedHistoryEvent> {
        val inclusive = after != null && before != null
        val where =
            listOfNotNull(
                "e.event_name = 'B3TR_ACTION'",
                to?.let { "e.to_address = :to" },
                appId?.let { "e.app_id = :app" },
                after?.let { "e.block_timestamp ${if (inclusive) ">=" else ">"} :after" },
                before?.let { "e.block_timestamp ${if (inclusive) "<=" else "<"} :before" },
            )
        return events(
            "$SELECT WHERE ${where.joinToString(" AND ")} ${order(direction)}",
            params(null, after, before, offset, limit)
                .addValue("to", to?.let(PostgresHex::bytes))
                .addValue("app", appId?.let(PostgresHex::bytes)),
        )
    }

    /** Names the enum does not know match nothing, so a filter left with none is an empty page. */
    private fun known(eventNames: List<String>?): Array<String>? =
        eventNames?.filter { it in KNOWN_NAMES }?.toTypedArray()

    private fun events(sql: String, params: MapSqlParameterSource): List<IndexedHistoryEvent> =
        jdbc.query(sql, params) { rs, _ -> HistoryRowMapping.assemble(HistoryRowMapping.row(rs)) }

    private fun params(
        names: Array<String>?,
        after: Long?,
        before: Long?,
        offset: Long,
        limit: Int,
    ) =
        MapSqlParameterSource("names", names)
            .addValue("after", after)
            .addValue("before", before)
            .addValue("offset", offset)
            .addValue("limit", limit)

    private fun order(direction: Direction) =
        "ORDER BY e.block_timestamp ${direction.name}, e.id ${direction.name} OFFSET :offset LIMIT :limit"

    companion object {
        private val KNOWN_NAMES = HistoryEventName.entries.map { it.name }.toSet()
        private const val SELECT = "SELECT e.* FROM history.event e"
        const val NOT_BLACKLISTED_TRANSFER =
            "NOT (e.event_name IN ('TRANSFER_NFT', 'TRANSFER_SF') AND EXISTS (SELECT 1 FROM " +
                "nft_blacklist.collection_state b WHERE b.contract_address = e.contract_address " +
                "AND b.superseded_at IS NULL AND b.is_blacklisted))"
    }
}
