package org.vechain.indexer.transfer

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.thor.model.BlockIdentifier

@Profile("transfers")
@Repository
open class PostgresTransferEventRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val namedJdbcTemplate: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
) : TransferEventRepository {

    private fun tableName(): String = "transfer_events"

    private fun mapRow(rs: ResultSet): IndexedTransferEvent {
        val topicsJson = rs.getString("topics")
        val topics =
            topicsJson?.let {
                objectMapper.readValue(it, object : TypeReference<List<String>>() {})
            } ?: emptyList()

        return IndexedTransferEvent(
            id = rs.getString("id"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            txId = rs.getString("tx_id"),
            from = rs.getString("from_address"),
            to = rs.getString("to_address"),
            value = rs.getString("value"),
            tokenAddress = rs.getString("token_address"),
            tokenId = rs.getString("token_id"),
            topics = topics,
            eventType = TransferEventType.valueOf(rs.getString("event_type")),
        )
    }

    private fun insertColumns(): String =
        """
        id, block_id, block_number, block_timestamp, tx_id, from_address, to_address,
        value, token_address, token_id, topics, event_type
        """
            .trimIndent()

    private fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?"

    private fun insertParams(event: IndexedTransferEvent): Array<Any?> =
        arrayOf(
            event.id,
            event.blockId,
            event.blockNumber,
            event.blockTimestamp,
            event.txId,
            event.from,
            event.to,
            event.value,
            event.tokenAddress,
            event.tokenId,
            objectMapper.writeValueAsString(event.topics),
            event.eventType.name,
        )

    @Transactional(rollbackFor = [Exception::class])
    override fun saveAll(events: List<IndexedTransferEvent>) {
        if (events.isEmpty()) return

        jdbcTemplate.batchUpdate(
            """
            INSERT INTO ${tableName()} (${insertColumns()})
            VALUES (${insertPlaceholders()})
            ON CONFLICT (id) DO UPDATE SET
                block_id = EXCLUDED.block_id,
                block_number = EXCLUDED.block_number,
                block_timestamp = EXCLUDED.block_timestamp,
                tx_id = EXCLUDED.tx_id,
                from_address = EXCLUDED.from_address,
                to_address = EXCLUDED.to_address,
                value = EXCLUDED.value,
                token_address = EXCLUDED.token_address,
                token_id = EXCLUDED.token_id,
                topics = EXCLUDED.topics,
                event_type = EXCLUDED.event_type
            """
                .trimIndent(),
            events.map { insertParams(it) },
        )
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun rollback(blockNumber: Long) {
        jdbcTemplate.update("DELETE FROM ${tableName()} WHERE block_number >= ?", blockNumber)
    }

    override fun getLatestBlockIdentifier(): BlockIdentifier? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT block_number, block_id FROM ${tableName()}
                ORDER BY block_number DESC
                LIMIT 1
                """
                    .trimIndent()
            ) { rs, _ ->
                BlockIdentifier(number = rs.getLong("block_number"), id = rs.getString("block_id"))
            }
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    private fun queryWithPagination(
        whereClause: String,
        pageable: Pageable,
        vararg params: Any?,
    ): Slice<IndexedTransferEvent> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE $whereClause
                ORDER BY block_number DESC, tx_id DESC, id DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                *params,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findByToOrFromAndTokenAddress(
        address: String,
        contractAddress: String,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent> {
        return queryWithPagination(
            "token_address = ? AND (to_address = ? OR from_address = ?)",
            pageable,
            contractAddress,
            address,
            address,
        )
    }

    override fun findByToOrFrom(
        to: String,
        from: String,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent> {
        return queryWithPagination("to_address = ? OR from_address = ?", pageable, to, from)
    }

    override fun findByTokenAddress(
        contractAddress: String,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent> {
        return queryWithPagination("token_address = ?", pageable, contractAddress)
    }

    override fun findByToAndTokenAddress(
        to: String,
        contractAddress: String,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent> {
        return queryWithPagination(
            "to_address = ? AND token_address = ?",
            pageable,
            to,
            contractAddress,
        )
    }

    override fun findByTo(to: String, pageable: Pageable): Slice<IndexedTransferEvent> {
        return queryWithPagination("to_address = ?", pageable, to)
    }

    override fun findByFrom(from: String, pageable: Pageable): Slice<IndexedTransferEvent> {
        return queryWithPagination("from_address = ?", pageable, from)
    }

    override fun findByFromAndTokenAddress(
        from: String,
        contractAddress: String,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent> {
        return queryWithPagination(
            "from_address = ? AND token_address = ?",
            pageable,
            from,
            contractAddress,
        )
    }

    override fun findByBlockNumberAndToOrFromIn(
        blockNumber: Long,
        addresses: List<String>,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent> {
        if (addresses.isEmpty()) {
            return SliceImpl(emptyList(), pageable, false)
        }

        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            namedJdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE block_number = :blockNumber
                AND (to_address IN (:addresses) OR from_address IN (:addresses))
                ORDER BY block_number DESC, tx_id DESC, id DESC
                LIMIT :limit OFFSET :offset
                """
                    .trimIndent(),
                mapOf(
                    "blockNumber" to blockNumber,
                    "addresses" to addresses,
                    "limit" to limit,
                    "offset" to offset,
                ),
            ) { rs, _ ->
                mapRow(rs)
            }

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }
}
