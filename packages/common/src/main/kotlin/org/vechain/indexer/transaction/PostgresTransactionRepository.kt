package org.vechain.indexer.transaction

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.thor.DecodedEvent
import org.vechain.indexer.thor.DecodedOutputs
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.thor.model.BlockIdentifier
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.TxTransfer

@Profile("transactions")
@Repository
class PostgresTransactionRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val namedJdbcTemplate: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
) : TransactionRepository {

    private val paramsType = object : TypeReference<Map<String, Any>>() {}

    @Transactional
    override fun saveAll(transactions: List<IndexedTransaction>): List<IndexedTransaction> {
        if (transactions.isEmpty()) {
            return transactions
        }

        val txIds = transactions.map { normaliseId(it.id) }
        namedJdbcTemplate.update(
            "DELETE FROM transactions WHERE id IN (:txIds)",
            mapOf("txIds" to txIds),
        )

        jdbcTemplate.batchUpdate(
            """
            INSERT INTO transactions (
                id,
                block_id,
                block_number,
                block_timestamp,
                type,
                size,
                chain_tag,
                block_ref,
                expiration,
                gas_price_coef,
                gas,
                max_fee_per_gas,
                max_priority_fee_per_gas,
                depends_on,
                nonce,
                gas_used,
                gas_payer,
                paid,
                reward,
                reverted,
                origin
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
                .trimIndent(),
            transactions
                .map { tx ->
                    val txId = normaliseId(tx.id)
                    arrayOf<Any?>(
                        txId,
                        tx.blockId,
                        tx.blockNumber,
                        tx.blockTimestamp,
                        tx.type,
                        tx.size,
                        tx.chainTag,
                        tx.blockRef,
                        tx.expiration,
                        tx.gasPriceCoef,
                        tx.gas,
                        tx.maxFeePerGas,
                        tx.maxPriorityFeePerGas,
                        tx.dependsOn,
                        tx.nonce,
                        tx.gasUsed,
                        normaliseAddress(tx.gasPayer),
                        tx.paid,
                        tx.reward,
                        tx.reverted,
                        normaliseAddress(tx.origin),
                    )
                }
                .toTypedArray(),
        )

        val clauseArgs =
            transactions.flatMap { tx ->
                val txId = normaliseId(tx.id)
                tx.clauses.mapIndexed { index, clause ->
                    arrayOf<Any?>(
                        txId,
                        index,
                        normaliseAddress(clause.to),
                        clause.value,
                        clause.data,
                    )
                }
            }
        if (clauseArgs.isNotEmpty()) {
            jdbcTemplate.batchUpdate(
                """
                INSERT INTO transaction_clauses (
                    tx_id,
                    clause_index,
                    to_address,
                    value,
                    data
                ) VALUES (?, ?, ?, ?, ?)
                """
                    .trimIndent(),
                clauseArgs.toTypedArray(),
            )
        }

        val outputArgs =
            transactions.flatMap { tx ->
                val txId = normaliseId(tx.id)
                tx.outputs.mapIndexed { outputIndex, output ->
                    arrayOf<Any?>(txId, outputIndex, normaliseAddress(output.contractAddress))
                }
            }
        if (outputArgs.isNotEmpty()) {
            jdbcTemplate.batchUpdate(
                """
                INSERT INTO transaction_outputs (
                    tx_id,
                    output_index,
                    contract_address
                ) VALUES (?, ?, ?)
                """
                    .trimIndent(),
                outputArgs.toTypedArray(),
            )
        }

        val eventArgs =
            transactions.flatMap { tx ->
                val txId = normaliseId(tx.id)
                tx.outputs.flatMapIndexed { outputIndex, output ->
                    output.events.mapIndexed { eventIndex, event ->
                        arrayOf<Any?>(
                            txId,
                            outputIndex,
                            eventIndex,
                            normaliseAddress(event.address),
                            event.data,
                            event.name,
                            event.params?.let { objectMapper.writeValueAsString(it) },
                        )
                    }
                }
            }
        if (eventArgs.isNotEmpty()) {
            jdbcTemplate.batchUpdate(
                """
                INSERT INTO transaction_output_events (
                    tx_id,
                    output_index,
                    event_index,
                    address,
                    data,
                    name,
                    params
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """
                    .trimIndent(),
                eventArgs.toTypedArray(),
            )
        }

        val topicArgs =
            transactions.flatMap { tx ->
                val txId = normaliseId(tx.id)
                tx.outputs.flatMapIndexed { outputIndex, output ->
                    output.events.flatMapIndexed { eventIndex, event ->
                        event.topics.mapIndexed { topicIndex, topic ->
                            arrayOf<Any?>(txId, outputIndex, eventIndex, topicIndex, topic)
                        }
                    }
                }
            }
        if (topicArgs.isNotEmpty()) {
            jdbcTemplate.batchUpdate(
                """
                INSERT INTO transaction_output_event_topics (
                    tx_id,
                    output_index,
                    event_index,
                    topic_index,
                    topic
                ) VALUES (?, ?, ?, ?, ?)
                """
                    .trimIndent(),
                topicArgs.toTypedArray(),
            )
        }

        val transferArgs =
            transactions.flatMap { tx ->
                val txId = normaliseId(tx.id)
                tx.outputs.flatMapIndexed { outputIndex, output ->
                    output.transfers.mapIndexed { transferIndex, transfer ->
                        arrayOf<Any?>(
                            txId,
                            outputIndex,
                            transferIndex,
                            normaliseAddress(transfer.sender),
                            normaliseAddress(transfer.recipient),
                            transfer.amount,
                        )
                    }
                }
            }
        if (transferArgs.isNotEmpty()) {
            jdbcTemplate.batchUpdate(
                """
                INSERT INTO transaction_output_transfers (
                    tx_id,
                    output_index,
                    transfer_index,
                    sender,
                    recipient,
                    amount
                ) VALUES (?, ?, ?, ?, ?, ?)
                """
                    .trimIndent(),
                transferArgs.toTypedArray(),
            )
        }

        return transactions
    }

    override fun findById(id: String): IndexedTransaction? {
        val rows =
            namedJdbcTemplate.query(
                """
                SELECT ${transactionColumns()}
                FROM transactions t
                WHERE t.id = :id
                """
                    .trimIndent(),
                mapOf("id" to normaliseId(id)),
                transactionRowMapper,
            )

        return buildTransactions(rows).firstOrNull()
    }

    override fun findByOrigin(origin: String, pageable: Pageable): Slice<IndexedTransaction> {
        return findSlice(
            "t.origin = :origin",
            mapOf("origin" to normaliseAddress(origin) ?: origin),
            pageable,
        )
    }

    override fun findByOriginOrGasPayer(
        origin: String,
        gasPayer: String,
        pageable: Pageable,
    ): Slice<IndexedTransaction> {
        return findSlice(
            "t.origin = :origin OR t.gas_payer = :gasPayer",
            mapOf(
                "origin" to normaliseAddress(origin) ?: origin,
                "gasPayer" to normaliseAddress(gasPayer) ?: gasPayer,
            ),
            pageable,
        )
    }

    override fun findByGasPayerAndOriginNot(
        gasPayer: String,
        origin: String,
        pageable: Pageable,
    ): Slice<IndexedTransaction> {
        return findSlice(
            "t.gas_payer = :gasPayer AND t.origin <> :origin",
            mapOf(
                "gasPayer" to normaliseAddress(gasPayer) ?: gasPayer,
                "origin" to normaliseAddress(origin) ?: origin,
            ),
            pageable,
        )
    }

    override fun findByContractAddress(
        contractAddress: String,
        pageable: Pageable,
    ): Slice<IndexedTransaction> {
        return findSlice(
            """
            EXISTS (
                SELECT 1
                FROM transaction_clauses c
                WHERE c.tx_id = t.id
                  AND c.to_address = :contractAddress
            )
            """
                .trimIndent(),
            mapOf("contractAddress" to normaliseAddress(contractAddress) ?: contractAddress),
            pageable,
        )
    }

    override fun findByContractAddresses(
        contractAddresses: List<String>,
        pageable: Pageable,
    ): Slice<IndexedTransaction> {
        if (contractAddresses.isEmpty()) {
            return SliceImpl(emptyList(), pageable, false)
        }

        val normalized = contractAddresses.mapNotNull { normaliseAddress(it) }
        return findSlice(
            """
            EXISTS (
                SELECT 1
                FROM transaction_clauses c
                WHERE c.tx_id = t.id
                  AND c.to_address IN (:contractAddresses)
            )
            """
                .trimIndent(),
            mapOf("contractAddresses" to normalized),
            pageable,
        )
    }

    override fun getLatestBlockIdentifier(): BlockIdentifier? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT block_number, block_id
                FROM transactions
                ORDER BY block_number DESC, id DESC
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

    @Transactional
    override fun deleteAllByBlockNumberGreaterThanEqual(start: Long) {
        jdbcTemplate.update("DELETE FROM transactions WHERE block_number >= ?", start)
    }

    private fun findSlice(
        whereClause: String,
        params: Map<String, Any>,
        pageable: Pageable,
    ): Slice<IndexedTransaction> {
        val orderBy = buildOrderBy(pageable.sort)
        val limit = pageable.pageSize + 1

        val sql =
            """
            SELECT ${transactionColumns()}
            FROM transactions t
            WHERE $whereClause
            $orderBy
            LIMIT :limit OFFSET :offset
            """
                .trimIndent()

        val parameterSource =
            MapSqlParameterSource(params)
                .addValue("limit", limit)
                .addValue("offset", pageable.offset)

        val rows = namedJdbcTemplate.query(sql, parameterSource, transactionRowMapper)
        if (rows.isEmpty()) {
            return SliceImpl(emptyList(), pageable, false)
        }

        val hasNext = rows.size > pageable.pageSize
        val pageRows = if (hasNext) rows.dropLast(1) else rows
        val transactions = buildTransactions(pageRows)

        return SliceImpl(transactions, pageable, hasNext)
    }

    private fun buildTransactions(rows: List<TransactionRow>): List<IndexedTransaction> {
        if (rows.isEmpty()) {
            return emptyList()
        }

        val txIds = rows.map { it.id }
        val clausesByTx = fetchClauses(txIds)
        val outputsByTx = fetchOutputs(txIds)
        val eventsByOutput = fetchEvents(txIds)
        val transfersByOutput = fetchTransfers(txIds)

        return rows.map { row ->
            val clauses = clausesByTx[row.id].orEmpty()
            val outputs =
                outputsByTx[row.id].orEmpty().map { outputRow ->
                    val outputKey = OutputKey(outputRow.txId, outputRow.outputIndex)
                    DecodedOutputs(
                        contractAddress = outputRow.contractAddress,
                        events = eventsByOutput[outputKey].orEmpty(),
                        transfers = transfersByOutput[outputKey].orEmpty(),
                    )
                }

            IndexedTransaction(
                id = row.id,
                blockId = row.blockId,
                blockNumber = row.blockNumber,
                blockTimestamp = row.blockTimestamp,
                type = row.type,
                size = row.size,
                chainTag = row.chainTag,
                blockRef = row.blockRef,
                expiration = row.expiration,
                clauses = clauses,
                gasPriceCoef = row.gasPriceCoef,
                gas = row.gas,
                maxFeePerGas = row.maxFeePerGas,
                maxPriorityFeePerGas = row.maxPriorityFeePerGas,
                dependsOn = row.dependsOn,
                nonce = row.nonce,
                gasUsed = row.gasUsed,
                gasPayer = row.gasPayer,
                paid = row.paid,
                reward = row.reward,
                reverted = row.reverted,
                origin = row.origin,
                outputs = outputs,
            )
        }
    }

    private fun fetchClauses(txIds: List<String>): Map<String, List<Clause>> {
        if (txIds.isEmpty()) {
            return emptyMap()
        }

        val clauses =
            namedJdbcTemplate.query(
                """
                SELECT tx_id, clause_index, to_address, value, data
                FROM transaction_clauses
                WHERE tx_id IN (:txIds)
                ORDER BY tx_id, clause_index
                """
                    .trimIndent(),
                mapOf("txIds" to txIds),
            ) { rs, _ ->
                ClauseRow(
                    txId = rs.getString("tx_id"),
                    clauseIndex = rs.getInt("clause_index"),
                    toAddress = rs.getString("to_address"),
                    value = rs.getString("value"),
                    data = rs.getString("data"),
                )
            }

        return clauses
            .groupBy { it.txId }
            .mapValues { (_, rows) ->
                rows
                    .sortedBy { it.clauseIndex }
                    .map { row -> Clause(to = row.toAddress, value = row.value, data = row.data) }
            }
    }

    private fun fetchOutputs(txIds: List<String>): Map<String, List<OutputRow>> {
        if (txIds.isEmpty()) {
            return emptyMap()
        }

        val outputs =
            namedJdbcTemplate.query(
                """
                SELECT tx_id, output_index, contract_address
                FROM transaction_outputs
                WHERE tx_id IN (:txIds)
                ORDER BY tx_id, output_index
                """
                    .trimIndent(),
                mapOf("txIds" to txIds),
            ) { rs, _ ->
                OutputRow(
                    txId = rs.getString("tx_id"),
                    outputIndex = rs.getInt("output_index"),
                    contractAddress = rs.getString("contract_address"),
                )
            }

        return outputs
            .groupBy { it.txId }
            .mapValues { (_, rows) -> rows.sortedBy { it.outputIndex } }
    }

    private fun fetchEvents(txIds: List<String>): Map<OutputKey, List<DecodedEvent>> {
        if (txIds.isEmpty()) {
            return emptyMap()
        }

        val topicsByEvent = fetchEventTopics(txIds)

        val events =
            namedJdbcTemplate.query(
                """
                SELECT tx_id, output_index, event_index, address, data, name, params
                FROM transaction_output_events
                WHERE tx_id IN (:txIds)
                ORDER BY tx_id, output_index, event_index
                """
                    .trimIndent(),
                mapOf("txIds" to txIds),
            ) { rs, _ ->
                EventRow(
                    txId = rs.getString("tx_id"),
                    outputIndex = rs.getInt("output_index"),
                    eventIndex = rs.getInt("event_index"),
                    address = rs.getString("address"),
                    data = rs.getString("data"),
                    name = rs.getString("name"),
                    params = rs.getString("params"),
                )
            }

        val eventsByOutput = mutableMapOf<OutputKey, MutableList<DecodedEvent>>()
        events.forEach { row ->
            val eventKey = EventKey(row.txId, row.outputIndex, row.eventIndex)
            val outputKey = OutputKey(row.txId, row.outputIndex)
            val topics = topicsByEvent[eventKey].orEmpty()
            val params = row.params?.let { objectMapper.readValue(it, paramsType) }
            val decodedEvent =
                DecodedEvent(
                    address = row.address,
                    topics = topics,
                    data = row.data,
                    name = row.name,
                    params = params,
                )
            eventsByOutput.getOrPut(outputKey) { mutableListOf() }.add(decodedEvent)
        }

        return eventsByOutput
    }

    private fun fetchEventTopics(txIds: List<String>): Map<EventKey, List<String>> {
        if (txIds.isEmpty()) {
            return emptyMap()
        }

        val topics =
            namedJdbcTemplate.query(
                """
                SELECT tx_id, output_index, event_index, topic_index, topic
                FROM transaction_output_event_topics
                WHERE tx_id IN (:txIds)
                ORDER BY tx_id, output_index, event_index, topic_index
                """
                    .trimIndent(),
                mapOf("txIds" to txIds),
            ) { rs, _ ->
                TopicRow(
                    txId = rs.getString("tx_id"),
                    outputIndex = rs.getInt("output_index"),
                    eventIndex = rs.getInt("event_index"),
                    topicIndex = rs.getInt("topic_index"),
                    topic = rs.getString("topic"),
                )
            }

        return topics
            .groupBy { EventKey(it.txId, it.outputIndex, it.eventIndex) }
            .mapValues { (_, rows) -> rows.sortedBy { it.topicIndex }.map { it.topic } }
    }

    private fun fetchTransfers(txIds: List<String>): Map<OutputKey, List<TxTransfer>> {
        if (txIds.isEmpty()) {
            return emptyMap()
        }

        val transfers =
            namedJdbcTemplate.query(
                """
                SELECT tx_id, output_index, transfer_index, sender, recipient, amount
                FROM transaction_output_transfers
                WHERE tx_id IN (:txIds)
                ORDER BY tx_id, output_index, transfer_index
                """
                    .trimIndent(),
                mapOf("txIds" to txIds),
            ) { rs, _ ->
                TransferRow(
                    txId = rs.getString("tx_id"),
                    outputIndex = rs.getInt("output_index"),
                    transferIndex = rs.getInt("transfer_index"),
                    sender = rs.getString("sender"),
                    recipient = rs.getString("recipient"),
                    amount = rs.getString("amount"),
                )
            }

        return transfers
            .groupBy { OutputKey(it.txId, it.outputIndex) }
            .mapValues { (_, rows) ->
                rows
                    .sortedBy { it.transferIndex }
                    .map { row ->
                        TxTransfer(
                            sender = row.sender,
                            recipient = row.recipient,
                            amount = row.amount,
                        )
                    }
            }
    }

    private fun buildOrderBy(sort: Sort): String {
        val mapping = mapOf("blockNumber" to "block_number", "_id" to "id")

        val orders =
            sort.mapNotNull { order ->
                val column = mapping[order.property] ?: return@mapNotNull null
                "$column ${order.direction.name}"
            }

        val orderClause =
            if (orders.isEmpty()) {
                "block_number DESC, id DESC"
            } else {
                orders.joinToString(", ")
            }

        return "ORDER BY $orderClause"
    }

    private fun transactionColumns(): String {
        return """
            t.id,
            t.block_id,
            t.block_number,
            t.block_timestamp,
            t.type,
            t.size,
            t.chain_tag,
            t.block_ref,
            t.expiration,
            t.gas_price_coef,
            t.gas,
            t.max_fee_per_gas,
            t.max_priority_fee_per_gas,
            t.depends_on,
            t.nonce,
            t.gas_used,
            t.gas_payer,
            t.paid,
            t.reward,
            t.reverted,
            t.origin
        """
            .trimIndent()
    }

    private fun normaliseAddress(address: String?): String? {
        return address?.let { HexUtils.normalise(it) }
    }

    private fun normaliseId(id: String): String {
        return HexUtils.normalise(id)
    }

    private val transactionRowMapper = RowMapper { rs: ResultSet, _: Int ->
        TransactionRow(
            id = rs.getString("id"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            type = rs.getLongOrNull("type"),
            size = rs.getLong("size"),
            chainTag = rs.getLong("chain_tag"),
            blockRef = rs.getString("block_ref"),
            expiration = rs.getLong("expiration"),
            gasPriceCoef = rs.getLongOrNull("gas_price_coef"),
            gas = rs.getLong("gas"),
            maxFeePerGas = rs.getString("max_fee_per_gas"),
            maxPriorityFeePerGas = rs.getString("max_priority_fee_per_gas"),
            dependsOn = rs.getString("depends_on"),
            nonce = rs.getString("nonce"),
            gasUsed = rs.getLong("gas_used"),
            gasPayer = rs.getString("gas_payer"),
            paid = rs.getString("paid"),
            reward = rs.getString("reward"),
            reverted = rs.getBoolean("reverted"),
            origin = rs.getString("origin"),
        )
    }

    private fun ResultSet.getLongOrNull(column: String): Long? {
        val value = getLong(column)
        return if (wasNull()) null else value
    }

    private data class TransactionRow(
        val id: String,
        val blockId: String,
        val blockNumber: Long,
        val blockTimestamp: Long,
        val type: Long?,
        val size: Long,
        val chainTag: Long,
        val blockRef: String,
        val expiration: Long,
        val gasPriceCoef: Long?,
        val gas: Long,
        val maxFeePerGas: String?,
        val maxPriorityFeePerGas: String?,
        val dependsOn: String?,
        val nonce: String,
        val gasUsed: Long,
        val gasPayer: String,
        val paid: String,
        val reward: String,
        val reverted: Boolean,
        val origin: String,
    )

    private data class ClauseRow(
        val txId: String,
        val clauseIndex: Int,
        val toAddress: String?,
        val value: String,
        val data: String,
    )

    private data class OutputRow(
        val txId: String,
        val outputIndex: Int,
        val contractAddress: String?,
    )

    private data class EventRow(
        val txId: String,
        val outputIndex: Int,
        val eventIndex: Int,
        val address: String,
        val data: String,
        val name: String?,
        val params: String?,
    )

    private data class TopicRow(
        val txId: String,
        val outputIndex: Int,
        val eventIndex: Int,
        val topicIndex: Int,
        val topic: String,
    )

    private data class TransferRow(
        val txId: String,
        val outputIndex: Int,
        val transferIndex: Int,
        val sender: String,
        val recipient: String,
        val amount: String,
    )

    private data class OutputKey(val txId: String, val outputIndex: Int)

    private data class EventKey(val txId: String, val outputIndex: Int, val eventIndex: Int)
}
