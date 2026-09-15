package org.vechain.indexer.accounts

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.explorer.TimestampUtils
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.ParamUtils.getAsString

/** Counts every address a block names for the first time, and writes the series row that moved. */
@Profile("accounts")
@Service
open class AccountTotalsSeriesService(private val repository: AccountsWriteRepository) {
    /** The addresses a block adds to the count and the series row it writes, if any. */
    data class Update(val newAccounts: List<String>, val totals: AccountTotalsSeries?)

    // Not @Volatile: the processor serialises processBlock() and resetCache() on one coroutine.
    private var lastProcessed: AccountTotalsSeries? = null

    open fun processBlock(block: Block, events: List<IndexedEvent>): Update {
        val previous = previousTotals(block.number)
        require(previous != null || block.number == 0L) {
            "Previous account totals record should exist for block ${block.number}"
        }
        val candidates = extractAccountIds(block, events)
        val newAccounts = (candidates - repository.findSeen(candidates)).toList()
        val count = newAccounts.size.toLong()
        val totals =
            if (previous == null) genesisTotals(block, count)
            else nextTotals(block, previous, count)
        return Update(newAccounts, totals)
    }

    /** Called once the block is saved, so the next block chains onto its row without a read. */
    open fun saved(totals: AccountTotalsSeries?) {
        if (totals != null) lastProcessed = totals
    }

    open fun resetCache() {
        lastProcessed = null
    }

    internal fun previousTotals(blockNumber: Long): AccountTotalsSeries? {
        if (blockNumber == 0L) return null
        val cached = lastProcessed
        if (cached != null && cached.blockNumber < blockNumber) return cached
        return repository.findTotalsBefore(blockNumber)
    }

    internal fun extractAccountIds(block: Block, events: List<IndexedEvent>): Set<String> =
        buildSet {
            block.transactions.forEach { tx ->
                normalizeAddress(tx.origin)?.let(::add)
                normalizeAddress(tx.gasPayer)?.let(::add)
                tx.clauses.forEach { clause -> normalizeAddress(clause.to)?.let(::add) }
            }
            if (block.number > 0L) normalizeAddress(block.beneficiary)?.let(::add)
            events
                .filter { it.eventType in OBSERVED_TRANSFER_EVENT_TYPES }
                .forEach { event ->
                    normalizeAddress(event.params.getAsString("from"))?.let(::add)
                    normalizeAddress(event.params.getAsString("to"))?.let(::add)
                }
        }

    internal fun genesisTotals(block: Block, newAccounts: Long): AccountTotalsSeries =
        AccountTotalsSeries(
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            totalAccounts = newAccounts,
            timeFrames = listOf(TimeFrame.HOUR, TimeFrame.DAY, TimeFrame.WEEK, TimeFrame.MONTH),
        )

    /** A row only when the count moved or a period closed since [previous]. */
    internal fun nextTotals(
        block: Block,
        previous: AccountTotalsSeries,
        newAccounts: Long,
    ): AccountTotalsSeries? {
        val frames = TimestampUtils.timeFrames(previous.blockTimestamp, block.timestamp)
        if (newAccounts == 0L && frames.isEmpty()) return null
        return AccountTotalsSeries(
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            totalAccounts = previous.totalAccounts + newAccounts,
            timeFrames = frames,
        )
    }

    private fun normalizeAddress(address: String?): String? {
        val normalized = address?.trim()?.takeUnless(String::isBlank)?.let(HexUtils::normalise)
        if (normalized == null || !Address(normalized).isValid()) return null
        return normalized.takeUnless { it == Address.ZERO_ADDRESS }
    }

    private companion object {
        val OBSERVED_TRANSFER_EVENT_TYPES =
            setOf("VET_TRANSFER", "Transfer", "TransferSingle", "TransferBatch")
    }
}
