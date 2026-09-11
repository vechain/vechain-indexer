package org.vechain.indexer.blocks

import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.bytesOrNull
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresHex.hexOrNull
import org.vechain.indexer.postgres.PostgresHex.minimalHex
import org.vechain.indexer.postgres.PostgresHex.minimalHexOrNull
import org.vechain.indexer.postgres.PostgresHex.quantity
import org.vechain.indexer.postgres.PostgresHex.quantityOrNull
import org.vechain.indexer.postgres.PostgresJson
import org.vechain.indexer.thor.DecodedEvent
import org.vechain.indexer.thor.DecodedOutputs
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.TxTransfer
import org.vechain.indexer.transaction.IndexedTransaction

/** The API models to rows and back: `assemble(flatten(tx))` is `tx` again. */
object BlocksRowMapping {

    /** Block fields every transaction row is joined with at read time. */
    data class BlockRef(val blockId: String, val blockTimestamp: Long)

    fun flattenBlock(block: IndexedBlock, totals: BlockTotals): BlockRow =
        BlockRow(
            number = block.blockNumber,
            id = bytes(block.blockId),
            parentId = bytes(block.parentID),
            timestamp = block.blockTimestamp,
            size = block.size.toInt(),
            gasLimit = block.gasLimit,
            gasUsed = block.gasUsed,
            beneficiary = bytes(block.beneficiary),
            signer = bytes(block.signer),
            totalScore = block.totalScore,
            txsRoot = bytes(block.txsRoot),
            txsFeatures = block.txsFeatures.toShort(),
            stateRoot = bytes(block.stateRoot),
            receiptsRoot = bytes(block.receiptsRoot),
            com = block.com,
            baseFeePerGas = quantityOrNull(block.baseFeePerGas),
            clauseCount = block.clauseCount,
            totalVthoPaid = quantity(block.totalVthoPaid),
            totals = totals,
        )

    fun assembleBlock(row: BlockRow, transactionIds: List<String>): IndexedBlock =
        IndexedBlock(
            blockNumber = row.number,
            blockId = hex(row.id),
            blockTimestamp = row.timestamp,
            size = row.size.toLong(),
            parentID = hex(row.parentId),
            gasLimit = row.gasLimit,
            gasUsed = row.gasUsed,
            beneficiary = hex(row.beneficiary),
            totalScore = row.totalScore,
            txsRoot = hex(row.txsRoot),
            txsFeatures = row.txsFeatures.toInt(),
            stateRoot = hex(row.stateRoot),
            receiptsRoot = hex(row.receiptsRoot),
            com = row.com,
            signer = hex(row.signer),
            baseFeePerGas = minimalHexOrNull(row.baseFeePerGas),
            clauseCount = row.clauseCount,
            totalVthoPaid = minimalHex(row.totalVthoPaid),
            transactions = transactionIds,
        )

    fun flatten(tx: IndexedTransaction): TransactionRows {
        val id = bytes(tx.id)
        val row =
            TransactionRow(
                id = id,
                blockNumber = tx.blockNumber,
                txIndex = tx.transactionIndex.toInt(),
                type = tx.type?.toShort(),
                size = tx.size.toInt(),
                chainTag = tx.chainTag.toShort(),
                blockRef = bytes(tx.blockRef),
                expiration = tx.expiration,
                gasPriceCoef = tx.gasPriceCoef?.toShort(),
                gas = tx.gas,
                maxFeePerGas = quantityOrNull(tx.maxFeePerGas),
                maxPriorityFeePerGas = quantityOrNull(tx.maxPriorityFeePerGas),
                dependsOn = bytesOrNull(tx.dependsOn),
                nonce = quantity(tx.nonce),
                gasUsed = tx.gasUsed,
                gasPayer = bytes(tx.gasPayer),
                paid = quantity(tx.paid),
                reward = quantity(tx.reward),
                reverted = tx.reverted,
                origin = bytes(tx.origin),
                outputCount = tx.outputs.size.toShort(),
            )
        val clauses =
            tx.clauses.mapIndexed { i, clause ->
                ClauseRow(
                    txId = id,
                    clauseIndex = i,
                    blockNumber = tx.blockNumber,
                    toAddress = bytesOrNull(clause.to),
                    value = quantity(clause.value),
                    data = bytes(clause.data),
                )
            }
        val events =
            tx.outputs.flatMapIndexed { clauseIndex, output ->
                output.events.mapIndexed { eventIndex, event ->
                    EventRow(
                        txId = id,
                        clauseIndex = clauseIndex,
                        eventIndex = eventIndex,
                        address = bytes(event.address),
                        topics = event.topics.map(::bytes),
                        data = bytes(event.data),
                        name = event.name,
                        params = PostgresJson.write(event.params),
                    )
                }
            }
        val transfers =
            tx.outputs.flatMapIndexed { clauseIndex, output ->
                output.transfers.mapIndexed { transferIndex, transfer ->
                    TransferRow(
                        txId = id,
                        clauseIndex = clauseIndex,
                        transferIndex = transferIndex,
                        sender = bytes(transfer.sender),
                        recipient = bytes(transfer.recipient),
                        amount = quantity(transfer.amount),
                    )
                }
            }
        return TransactionRows(row, clauses, events, transfers)
    }

    /** [events] and [transfers] may arrive in any order; they are placed by their indexes. */
    fun assemble(
        row: TransactionRow,
        block: BlockRef,
        clauses: List<ClauseRow>,
        events: List<EventRow>,
        transfers: List<TransferRow>,
    ): IndexedTransaction {
        val origin = hex(row.origin)
        val eventsByClause = events.groupBy { it.clauseIndex }
        val transfersByClause = transfers.groupBy { it.clauseIndex }
        return IndexedTransaction(
            id = hex(row.id),
            blockId = block.blockId,
            blockNumber = row.blockNumber,
            blockTimestamp = block.blockTimestamp,
            transactionIndex = row.txIndex.toLong(),
            type = row.type?.toLong(),
            size = row.size.toLong(),
            chainTag = row.chainTag.toLong(),
            blockRef = hex(row.blockRef),
            expiration = row.expiration,
            clauses =
                clauses
                    .sortedBy { it.clauseIndex }
                    .map { Clause(hexOrNull(it.toAddress), minimalHex(it.value), hex(it.data)) },
            gasPriceCoef = row.gasPriceCoef?.toLong(),
            gas = row.gas,
            maxFeePerGas = minimalHexOrNull(row.maxFeePerGas),
            maxPriorityFeePerGas = minimalHexOrNull(row.maxPriorityFeePerGas),
            dependsOn = hexOrNull(row.dependsOn),
            nonce = minimalHex(row.nonce),
            gasUsed = row.gasUsed,
            gasPayer = hex(row.gasPayer),
            paid = minimalHex(row.paid),
            reward = minimalHex(row.reward),
            reverted = row.reverted,
            origin = origin,
            outputs =
                (0 until row.outputCount).map { clauseIndex ->
                    DecodedOutputs(
                        contractAddress = origin,
                        events =
                            eventsByClause[clauseIndex]
                                .orEmpty()
                                .sortedBy { it.eventIndex }
                                .map {
                                    DecodedEvent(
                                        address = hex(it.address),
                                        topics = it.topics.map(::hex),
                                        data = hex(it.data),
                                        name = it.name,
                                        params = PostgresJson.read(it.params),
                                    )
                                },
                        transfers =
                            transfersByClause[clauseIndex]
                                .orEmpty()
                                .sortedBy { it.transferIndex }
                                .map {
                                    TxTransfer(
                                        hex(it.sender),
                                        hex(it.recipient),
                                        minimalHex(it.amount),
                                    )
                                },
                    )
                },
        )
    }
}
