package org.vechain.indexer.utils

import java.time.Instant
import java.time.ZoneId
import org.vechain.indexer.thor.model.*

object BlockUtils {
    /** Get all confirmed transactions from a block */
    private fun confirmedTransactions(block: Block): List<Transaction> =
        block.transactions.filter { !it.reverted }

    /**
     * Get all outputs from a block, paired with the transaction that created it.
     *
     * DOES NOT include reverted TXs
     */
    fun getOutputs(block: Block): List<Pair<TxOutputs, Transaction>> =
        confirmedTransactions(block).flatMap { tx -> tx.outputs.map { output -> Pair(output, tx) } }

    /**
     * Converts a given timestamp (in seconds) to a `LocalDate` string in the UTC time zone
     *
     * @param timestamp The timestamp in seconds
     * @return A string representing the `LocalDate` in the format `YYYY-MM-DD`
     */
    fun getDateAtUTC(timestamp: Long): String =
        Instant.ofEpochSecond(timestamp).atZone(ZoneId.of("UTC")).toLocalDate().toString()
}
