package org.vechain.indexer.accounts

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.accounts.repository.AccountTotalsSeriesRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.explorer.TimestampUtils.calculateTimeBoundary
import org.vechain.indexer.explorer.TimestampUtils.isDaily
import org.vechain.indexer.explorer.TimestampUtils.isHourly
import org.vechain.indexer.explorer.TimestampUtils.isMonthly
import org.vechain.indexer.explorer.TimestampUtils.isWeekly
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.CacheUtils
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("accounts", "account-totals-series")
@Service
open class AccountTotalsSeriesService(private val repository: AccountTotalsSeriesRepository) {
    @Volatile private var lastProcessedSeries: AccountTotalsSeries? = null

    open fun processBlock(block: Block, events: List<IndexedEvent>): List<AccountTotalsSeries> {
        val previousSeries = getPreviousSeries(block.number)
        validatePreviousSeries(previousSeries, block.number)

        val newAccounts = getNewAccountMarkers(block, events)
        val newAccountsCount = newAccounts.size.toLong()

        return buildList {
            addAll(newAccounts)

            val seriesRecord =
                if (previousSeries == null) {
                    createGenesisSeries(block, newAccountsCount)
                } else {
                    createSeriesIfChangedOrBoundary(block, previousSeries, newAccountsCount)
                }

            if (seriesRecord != null) {
                add(seriesRecord)
            }
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(records: List<AccountTotalsSeries>) {
        if (records.isEmpty()) return

        repository.saveAll(records)

        val latestSeries = records.last { it.recordType == AccountTotalsSeriesRecordType.SERIES }
        CacheUtils.updateAfterCommit(
            latestSeries,
            setter = { lastProcessedSeries = it },
            clear = ::clearProcessingState,
        )
    }

    open fun clearProcessingState() {
        lastProcessedSeries = null
    }

    internal fun getPreviousSeries(blockNumber: Long): AccountTotalsSeries? {
        if (blockNumber == 0L) {
            return null
        }

        val cached = lastProcessedSeries
        if (cached != null && cached.blockNumber < blockNumber) {
            return cached
        }

        return repository.findFirstByRecordTypeAndBlockNumberLessThanOrderByBlockNumberDesc(
            AccountTotalsSeriesRecordType.SERIES,
            blockNumber,
        )
    }

    internal fun validatePreviousSeries(
        previousSeries: AccountTotalsSeries?,
        currentBlockNumber: Long,
    ) {
        require(previousSeries != null || currentBlockNumber == 0L) {
            "Previous account totals record should exist for block $currentBlockNumber"
        }
    }

    internal fun getNewAccountMarkers(
        block: Block,
        events: List<IndexedEvent>,
    ): List<AccountTotalsSeries> {
        val candidateAccountIds = extractAccountIds(block, events)
        if (candidateAccountIds.isEmpty()) return emptyList()

        val markerIds = candidateAccountIds.map(::accountId)
        val existingIds = repository.findAllById(markerIds).map { it.id }.toSet()

        return markerIds.filterNot(existingIds::contains).map { markerId ->
            AccountTotalsSeries(
                id = markerId,
                recordType = AccountTotalsSeriesRecordType.ACCOUNT,
                address = markerId.removePrefix(ACCOUNT_PREFIX),
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                totalAccounts = null,
                isHourly = null,
                isDaily = null,
                isWeekly = null,
                isMonthly = null,
            )
        }
    }

    internal fun extractAccountIds(block: Block, events: List<IndexedEvent>): Set<String> =
        buildSet {
            block.transactions.forEach { tx ->
                normalizeAddress(tx.origin)?.let(::add)
                normalizeAddress(tx.gasPayer)?.let(::add)
                tx.clauses.forEach { clause -> normalizeAddress(clause.to)?.let(::add) }
            }

            if (block.number > 0L) {
                normalizeAddress(block.beneficiary)?.let(::add)
            }

            events.filter(::isObservedTransferEvent).forEach { event ->
                normalizeAddress(event.params.getAsString("from"))?.let(::add)
                normalizeAddress(event.params.getAsString("to"))?.let(::add)
            }
        }

    internal fun createGenesisSeries(block: Block, newAccountsCount: Long): AccountTotalsSeries =
        AccountTotalsSeries(
            id = seriesId(block.number),
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            totalAccounts = newAccountsCount,
            isHourly = true,
            isDaily = true,
            isWeekly = true,
            isMonthly = true,
        )

    internal fun createSeries(
        block: Block,
        previousSeries: AccountTotalsSeries,
        newAccountsCount: Long,
    ): AccountTotalsSeries =
        AccountTotalsSeries(
            id = seriesId(block.number),
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            totalAccounts = (previousSeries.totalAccounts ?: 0L) + newAccountsCount,
            isHourly =
                calculateTimeBoundary(previousSeries.blockTimestamp, block.timestamp, ::isHourly),
            isDaily =
                calculateTimeBoundary(previousSeries.blockTimestamp, block.timestamp, ::isDaily),
            isWeekly =
                calculateTimeBoundary(previousSeries.blockTimestamp, block.timestamp, ::isWeekly),
            isMonthly =
                calculateTimeBoundary(previousSeries.blockTimestamp, block.timestamp, ::isMonthly),
        )

    internal fun createSeriesIfChangedOrBoundary(
        block: Block,
        previousSeries: AccountTotalsSeries,
        newAccountsCount: Long,
    ): AccountTotalsSeries? {
        val series = createSeries(block, previousSeries, newAccountsCount)
        val boundaryCrossed =
            series.isHourly == true ||
                series.isDaily == true ||
                series.isWeekly == true ||
                series.isMonthly == true

        return if (newAccountsCount > 0L || boundaryCrossed) {
            series
        } else {
            null
        }
    }

    internal fun seriesId(blockNumber: Long): String = "$SERIES_PREFIX$blockNumber"

    internal fun accountId(address: String): String = "$ACCOUNT_PREFIX$address"

    private fun isObservedTransferEvent(event: IndexedEvent): Boolean =
        event.eventType in OBSERVED_TRANSFER_EVENT_TYPES

    private fun normalizeAddress(address: String?): String? {
        val normalized = address?.trim()?.takeUnless(String::isBlank)?.let(HexUtils::normalise)
        if (normalized == null || !Address(normalized).isValid()) {
            return null
        }
        return normalized.takeUnless { it == Address.ZERO_ADDRESS }
    }

    private companion object {
        const val SERIES_PREFIX = "series-"
        const val ACCOUNT_PREFIX = "account-"
        val OBSERVED_TRANSFER_EVENT_TYPES =
            setOf("VET_TRANSFER", "Transfer", "TransferSingle", "TransferBatch")
    }
}
