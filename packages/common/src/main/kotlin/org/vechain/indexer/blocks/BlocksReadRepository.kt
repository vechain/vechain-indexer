package org.vechain.indexer.blocks

import java.sql.ResultSet
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.blocks.BlocksRowMapping.BlockRef
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex
import org.vechain.indexer.transaction.IndexedTransaction
import org.vechain.indexer.transaction.TransactionCountSummary

/** The API's reads from the Postgres tables: one statement per page, three more when expanded. */
@Repository
@ConditionalOnPostgres
open class BlocksReadRepository(@Qualifier("postgresJdbcTemplate") jdbcTemplate: JdbcTemplate) {

    private val jdbc = NamedParameterJdbcTemplate(jdbcTemplate)

    /** The last row of the previous `/transactions/latest` page. */
    data class LatestCursor(val blockNumber: Long, val txIndex: Int)

    private class TxWithBlock(
        val row: TransactionRow,
        val block: BlockRef,
        val clauseCount: Int,
    )

    /** Newest first, from [from] inclusive or the indexed head, [limit] rows. */
    open fun findBlocks(from: Long?, limit: Int): List<IndexedBlock> {
        val bound = if (from == null) "" else "WHERE b.number <= :from"
        return jdbc.query(
            """
            SELECT b.*, (SELECT array_agg(t.id ORDER BY t.tx_index) FROM blocks.transaction t
                         WHERE t.block_number = b.number) AS tx_ids
            FROM blocks.block b $bound ORDER BY b.number DESC LIMIT :limit
            """
                .trimIndent(),
            MapSqlParameterSource("from", from).addValue("limit", limit),
        ) { rs, _ ->
            val ids = rs.getArray("tx_ids")?.array as Array<*>?
            BlocksRowMapping.assembleBlock(
                BlocksRowMappers.block(rs),
                ids.orEmpty().map { PostgresHex.hex(it as ByteArray) },
            )
        }
    }

    /** Block number descending, canonical order within a block, continuing after [after]. */
    open fun findLatest(after: LatestCursor?, limit: Int): List<IndexedTransaction> {
        val where =
            if (after == null) ""
            else "WHERE t.block_number < :b OR (t.block_number = :b AND t.tx_index > :i)"
        val params =
            MapSqlParameterSource("limit", limit)
                .addValue("b", after?.blockNumber)
                .addValue("i", after?.txIndex)
        return transactions(
            "$SELECT_TX $where ORDER BY t.block_number DESC, t.tx_index ASC LIMIT :limit",
            params,
            expanded = false,
        )
    }

    open fun findById(id: String): IndexedTransaction? =
        transactions(
                "$SELECT_TX WHERE t.id = :id",
                MapSqlParameterSource("id", PostgresHex.bytes(id)),
                expanded = true,
            )
            .firstOrNull()

    open fun findByOrigin(
        origin: String,
        includeDelegated: Boolean,
        offset: Long,
        limit: Int,
        direction: Direction,
        expanded: Boolean,
    ): List<IndexedTransaction> {
        val order = "block_number ${direction.name}, id ${direction.name}"
        val params =
            MapSqlParameterSource("address", PostgresHex.bytes(origin))
                .addValue("offset", offset)
                .addValue("limit", limit)
                .addValue("reach", offset + limit)
        val sql =
            if (includeDelegated)
                """
                SELECT t.*, b.id AS block_id, b.timestamp AS block_timestamp, $CLAUSE_COUNT
                FROM ((SELECT * FROM blocks.transaction WHERE origin = :address ORDER BY $order LIMIT :reach)
                      UNION
                      (SELECT * FROM blocks.transaction WHERE gas_payer = :address ORDER BY $order LIMIT :reach)) t
                JOIN blocks.block b ON b.number = t.block_number
                ORDER BY t.block_number ${direction.name}, t.id ${direction.name} OFFSET :offset LIMIT :limit
                """
                    .trimIndent()
            else
                "$SELECT_TX WHERE t.origin = :address " +
                    "ORDER BY t.block_number ${direction.name}, t.id ${direction.name} OFFSET :offset LIMIT :limit"
        return transactions(sql, params, expanded)
    }

    open fun findDelegated(
        gasPayer: String,
        offset: Long,
        limit: Int,
        direction: Direction,
        expanded: Boolean,
    ): List<IndexedTransaction> =
        transactions(
            "$SELECT_TX WHERE t.gas_payer = :address AND t.origin <> :address " +
                "ORDER BY t.block_number ${direction.name}, t.id ${direction.name} OFFSET :offset LIMIT :limit",
            MapSqlParameterSource("address", PostgresHex.bytes(gasPayer))
                .addValue("offset", offset)
                .addValue("limit", limit),
            expanded,
        )

    /** DISTINCT ON keeps Mongo's multikey semantics: two clauses to one contract is one hit. */
    open fun findByContract(
        contractAddress: String,
        offset: Long,
        limit: Int,
        direction: Direction,
        expanded: Boolean,
    ): List<IndexedTransaction> =
        transactions(
            """
            WITH page AS (
              SELECT DISTINCT ON (c.block_number, c.tx_id) c.block_number, c.tx_id FROM blocks.clause c
              WHERE c.to_address = :address
              ORDER BY c.block_number ${direction.name}, c.tx_id ${direction.name} OFFSET :offset LIMIT :limit)
            SELECT t.*, b.id AS block_id, b.timestamp AS block_timestamp, $CLAUSE_COUNT
            FROM page p JOIN blocks.transaction t ON t.id = p.tx_id JOIN blocks.block b ON b.number = t.block_number
            ORDER BY p.block_number ${direction.name}, p.tx_id ${direction.name}
            """
                .trimIndent(),
            MapSqlParameterSource("address", PostgresHex.bytes(contractAddress))
                .addValue("offset", offset)
                .addValue("limit", limit),
            expanded,
        )

    open fun latestTotals(): TransactionCountSummary? =
        jdbc
            .query(
                "SELECT number, id, timestamp, total_transactions, total_clauses, " +
                    "total_reverted_transactions, total_reverted_clauses " +
                    "FROM blocks.block ORDER BY number DESC LIMIT 1"
            ) { rs, _ ->
                val totals = BlocksRowMappers.totals(rs)
                TransactionCountSummary(
                    blockId = PostgresHex.hex(rs.getBytes("id")),
                    blockNumber = rs.getLong("number"),
                    blockTimestamp = rs.getLong("timestamp"),
                    totalTransactions = totals.totalTransactions.toBigInteger(),
                    totalClauses = totals.totalClauses.toBigInteger(),
                    totalRevertedTransactions = totals.totalRevertedTransactions.toBigInteger(),
                    totalRevertedClauses = totals.totalRevertedClauses.toBigInteger(),
                )
            }
            .firstOrNull()

    private fun transactions(
        sql: String,
        params: MapSqlParameterSource,
        expanded: Boolean,
    ): List<IndexedTransaction> {
        val page = jdbc.query(sql, params) { rs, _ -> txWithBlock(rs) }
        if (page.isEmpty()) return emptyList()
        if (!expanded) {
            return page.map {
                BlocksRowMapping.assemble(
                    it.row,
                    it.block,
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    it.clauseCount,
                )
            }
        }
        val ids = MapSqlParameterSource("ids", page.map { it.row.id }.toTypedArray())
        val clauses = children(ids, SELECT_CLAUSES, BlocksRowMappers::clause) { it.txId }
        val events = children(ids, SELECT_EVENTS, BlocksRowMappers::event) { it.txId }
        val transfers = children(ids, SELECT_TRANSFERS, BlocksRowMappers::transfer) { it.txId }
        return page.map {
            val key = PostgresHex.hex(it.row.id)
            BlocksRowMapping.assemble(
                it.row,
                it.block,
                clauses[key].orEmpty(),
                events[key].orEmpty(),
                transfers[key].orEmpty(),
            )
        }
    }

    private fun <T> children(
        ids: MapSqlParameterSource,
        sql: String,
        map: (ResultSet) -> T,
        txId: (T) -> ByteArray,
    ): Map<String, List<T>> =
        jdbc.query(sql, ids) { rs, _ -> map(rs) }.groupBy { PostgresHex.hex(txId(it)) }

    private fun txWithBlock(rs: ResultSet) =
        TxWithBlock(
            row = BlocksRowMappers.transaction(rs),
            block =
                BlockRef(PostgresHex.hex(rs.getBytes("block_id")), rs.getLong("block_timestamp")),
            clauseCount = rs.getInt("clause_count"),
        )

    companion object {
        private const val CLAUSE_COUNT =
            "(SELECT count(*) FROM blocks.clause c WHERE c.tx_id = t.id) AS clause_count"
        private const val SELECT_CLAUSES = "SELECT * FROM blocks.clause WHERE tx_id = ANY(:ids)"
        private const val SELECT_EVENTS = "SELECT * FROM blocks.event WHERE tx_id = ANY(:ids)"
        private const val SELECT_TRANSFERS = "SELECT * FROM blocks.transfer WHERE tx_id = ANY(:ids)"
        private const val SELECT_TX =
            "SELECT t.*, b.id AS block_id, b.timestamp AS block_timestamp, $CLAUSE_COUNT " +
                "FROM blocks.transaction t JOIN blocks.block b ON b.number = t.block_number"
    }
}
