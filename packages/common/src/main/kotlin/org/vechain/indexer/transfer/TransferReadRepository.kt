package org.vechain.indexer.transfer

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex
import org.vechain.indexer.transfer.TransferRowMapping.INTERACTION_TABLE
import org.vechain.indexer.transfer.TransferRowMapping.TABLE

/** Where a `/transfers/latest` page stopped: the last row served, by block and place in it. */
data class LatestTransferCursor(val blockNumber: Long, val transferIndex: Long)

/** The API's transfer reads: one statement per page, each an index range scan. */
@Repository
@ConditionalOnPostgres
open class TransferReadRepository(@Qualifier("postgresJdbcTemplate") jdbcTemplate: JdbcTemplate) {

    private val jdbc = NamedParameterJdbcTemplate(jdbcTemplate)

    /**
     * `/transfers`, `/transfers/to`, `/transfers/from`: paged by time, in-block order within it.
     */
    open fun find(
        to: String?,
        from: String?,
        toOrFrom: String?,
        tokenAddress: String?,
        eventTypes: List<TransferEventType>?,
        after: Long?,
        before: Long?,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<IndexedTransferEvent> {
        val filters =
            listOfNotNull(
                tokenAddress?.let { "token_address = :token" },
                eventTypes
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { "event_type = ANY(CAST(:types AS transfers.event_type[]))" },
                after?.let { "block_timestamp >= :after" },
                before?.let { "block_timestamp <= :before" },
            )
        val order = "block_timestamp ${direction.name}, transfer_index ${direction.name}"
        val page = "ORDER BY $order OFFSET :offset LIMIT :limit"
        val sql =
            if (toOrFrom != null) {
                // One index range per side the address is on; the from side skips self-transfers.
                val toSide = where(listOf("to_address = :address") + filters)
                val fromSide =
                    where(listOf("from_address = :address", "to_address <> :address") + filters)
                "SELECT * FROM ((SELECT * FROM $TABLE $toSide ORDER BY $order LIMIT :reach) " +
                    "UNION ALL (SELECT * FROM $TABLE $fromSide ORDER BY $order LIMIT :reach)) u $page"
            } else {
                val sides =
                    listOfNotNull(
                        to?.let { "to_address = :to" },
                        from?.let { "from_address = :from" },
                    )
                "SELECT * FROM $TABLE ${where(sides + filters)} $page"
            }
        val params =
            MapSqlParameterSource()
                .addValue("address", toOrFrom?.let(PostgresHex::bytes))
                .addValue("to", to?.let(PostgresHex::bytes))
                .addValue("from", from?.let(PostgresHex::bytes))
                .addValue("token", tokenAddress?.let(PostgresHex::bytes))
                .addValue("types", eventTypes?.map { it.name }?.toTypedArray())
                .addValue("after", after)
                .addValue("before", before)
                .addValue("offset", offset)
                .addValue("limit", limit)
                .addValue("reach", offset + limit)
        return transfers(sql, params)
    }

    /** `/transfers/forBlock`: one block's transfers touching any of [addresses]. */
    open fun findByBlockNumber(
        blockNumber: Long,
        addresses: List<String>,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<IndexedTransferEvent> =
        transfers(
            "SELECT * FROM $TABLE WHERE block_number = :block " +
                "AND (to_address = ANY(:addresses) OR from_address = ANY(:addresses)) " +
                "ORDER BY transfer_index ${direction.name} OFFSET :offset LIMIT :limit",
            MapSqlParameterSource("block", blockNumber)
                .addValue("addresses", addresses.map(PostgresHex::bytes).toTypedArray())
                .addValue("offset", offset)
                .addValue("limit", limit),
        )

    /** `/transfers/latest`: newest block first, in-block order within it, from the cursor on. */
    open fun findLatest(
        eventTypes: Collection<TransferEventType>,
        cursor: LatestTransferCursor?,
        limit: Int,
    ): List<IndexedTransferEvent> {
        val order = "ORDER BY block_number DESC, transfer_index"
        // The bound on block_number alone is what lets the scan start at the cursor.
        val keyset = cursor?.let {
            "block_number <= :block AND (block_number < :block OR transfer_index > :index)"
        }
        val types = eventTypes.distinct()
        val typed = types.indices.map { "event_type = CAST(:type$it AS transfers.event_type)" }
        val sql =
            when (types.size) {
                TransferEventType.entries.size ->
                    "SELECT * FROM $TABLE ${where(listOfNotNull(keyset))} $order LIMIT :limit"
                1 ->
                    "SELECT * FROM $TABLE ${where(listOfNotNull(typed[0], keyset))} $order LIMIT :limit"
                // One ordered scan per type, merged; a single IN would sort every row of every
                // type.
                else ->
                    typed.joinToString(
                        " UNION ALL ",
                        "SELECT * FROM (",
                        ") u $order LIMIT :limit",
                    ) {
                        "(SELECT * FROM $TABLE ${where(listOfNotNull(it, keyset))} $order LIMIT :limit)"
                    }
            }
        val params =
            MapSqlParameterSource("limit", limit)
                .addValue("block", cursor?.blockNumber)
                .addValue("index", cursor?.transferIndex)
        types.forEachIndexed { i, t -> params.addValue("type$i", t.name) }
        return transfers(sql, params)
    }

    /** `/transfers/fungible-tokens-contracts`: newest touch first, optionally among [contracts]. */
    open fun findInteractedContracts(
        wallet: String,
        contracts: List<String>?,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<String> {
        if (contracts != null && contracts.isEmpty()) return emptyList()
        val among = contracts?.let { "AND contract_address = ANY(:contracts)" } ?: ""
        return jdbc.query(
            "SELECT contract_address FROM $INTERACTION_TABLE WHERE wallet_address = :wallet $among " +
                "ORDER BY block_number ${direction.name}, contract_address ${direction.name} " +
                "OFFSET :offset LIMIT :limit",
            MapSqlParameterSource("wallet", PostgresHex.bytes(wallet))
                .addValue("contracts", contracts?.map(PostgresHex::bytes)?.toTypedArray())
                .addValue("offset", offset)
                .addValue("limit", limit),
        ) { rs, _ ->
            PostgresHex.hex(rs.getBytes(1))
        }
    }

    /** Every transfer of one type, for the e2e chain, which is small enough to list. */
    open fun findAllByEventType(eventType: TransferEventType): List<IndexedTransferEvent> =
        transfers(
            "SELECT * FROM $TABLE WHERE event_type = CAST(:type AS transfers.event_type) " +
                "ORDER BY block_number, transfer_index",
            MapSqlParameterSource("type", eventType.name),
        )

    private fun transfers(sql: String, params: MapSqlParameterSource): List<IndexedTransferEvent> =
        jdbc.query(sql, params) { rs, _ -> TransferRowMapping.read(rs) }

    private fun where(filters: List<String>): String =
        if (filters.isEmpty()) "" else "WHERE " + filters.joinToString(" AND ")
}
