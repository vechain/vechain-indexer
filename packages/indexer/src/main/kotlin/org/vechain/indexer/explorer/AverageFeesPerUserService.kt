package org.vechain.indexer.explorer

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneOffset
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.explorer.repository.AverageFeesPerUserRepository
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.BlockUtils
import org.vechain.indexer.utils.CacheUtils
import org.vechain.indexer.utils.NumberUtils.hexToBigInteger
import org.vechain.indexer.utils.scaleDown

@Profile("explorer", "average-fees-per-user")
@Service
open class AverageFeesPerUserService(private val repository: AverageFeesPerUserRepository) {
    @Volatile private var lastProcessedDailyMetric: AverageFeesPerUser? = null

    open fun processBlock(block: Block): List<AverageFeesPerUser> {
        if (block.transactions.isEmpty()) {
            return emptyList()
        }

        val date = BlockUtils.getDateAtUTC(block.timestamp)
        val dayStartTimestamp = getDayStartTimestamp(block.timestamp)
        val existingSummary = getPreviousSummary(date, block.number)

        val distinctOrigins = block.transactions.map { it.origin.lowercase() }.toSet()
        val markerIds = distinctOrigins.map { markerId(date, it) }
        val existingMarkerIds = repository.findAllById(markerIds).map { it.id }.toSet()
        val newMarkers =
            markerIds.filterNot(existingMarkerIds::contains).map { id ->
                val origin = id.removePrefix("$ORIGIN_MARKER_PREFIX$date-")
                AverageFeesPerUser(
                    id = id,
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    recordType = AverageFeesPerUserRecordType.ORIGIN_MARKER,
                    date = date,
                    origin = origin,
                )
            }

        val feesPaidInBlock =
            block.transactions.fold(BigDecimal.ZERO) { total, tx ->
                total + scaleDown(tx.paid.hexToBigInteger(), 18)
            }
        val updatedSummary =
            createOrUpdateSummary(
                block = block,
                date = date,
                dayStartTimestamp = dayStartTimestamp,
                feesPaidInBlock = feesPaidInBlock,
                newUsersInBlock = newMarkers.size.toLong(),
                existingSummary = existingSummary,
            )

        return buildList {
            addAll(newMarkers)
            add(updatedSummary)
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(records: List<AverageFeesPerUser>) {
        if (records.isEmpty()) return

        repository.saveAll(records)

        val latestSummary = records.last { it.recordType == AverageFeesPerUserRecordType.SUMMARY }
        CacheUtils.updateAfterCommit(
            latestSummary,
            setter = { lastProcessedDailyMetric = it },
            clear = ::clearProcessingState,
        )
    }

    open fun clearProcessingState() {
        lastProcessedDailyMetric = null
    }

    internal fun getPreviousSummary(date: String, blockNumber: Long): AverageFeesPerUser? {
        val cached = lastProcessedDailyMetric
        if (cached != null && cached.date == date && cached.blockNumber < blockNumber) {
            return cached
        }

        return repository.findFirstByRecordTypeAndDateAndBlockNumberLessThanOrderByBlockNumberDesc(
            AverageFeesPerUserRecordType.SUMMARY,
            date,
            blockNumber,
        )
    }

    internal fun createOrUpdateSummary(
        block: Block,
        date: String,
        dayStartTimestamp: Long,
        feesPaidInBlock: BigDecimal,
        newUsersInBlock: Long,
        existingSummary: AverageFeesPerUser?,
    ): AverageFeesPerUser {
        val totalFeesPaid = (existingSummary?.totalFeesPaid ?: BigDecimal.ZERO) + feesPaidInBlock
        val dailyActiveUsers = (existingSummary?.dailyActiveUsers ?: 0L) + newUsersInBlock

        return AverageFeesPerUser(
            id = summaryId(block.number),
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            recordType = AverageFeesPerUserRecordType.SUMMARY,
            date = date,
            dayStartTimestamp = dayStartTimestamp,
            totalFeesPaid = totalFeesPaid,
            dailyActiveUsers = dailyActiveUsers,
            averageFeesPerUser = calculateAverage(totalFeesPaid, dailyActiveUsers),
        )
    }

    internal fun calculateAverage(totalFeesPaid: BigDecimal, dailyActiveUsers: Long): BigDecimal =
        if (dailyActiveUsers == 0L) {
            BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP)
        } else {
            totalFeesPaid.divide(BigDecimal.valueOf(dailyActiveUsers), SCALE, RoundingMode.HALF_UP)
        }

    internal fun getDayStartTimestamp(timestamp: Long): Long =
        Instant.ofEpochSecond(timestamp)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toEpochSecond()

    internal fun summaryId(blockNumber: Long): String = "$SUMMARY_PREFIX$blockNumber"

    internal fun markerId(date: String, origin: String): String =
        "$ORIGIN_MARKER_PREFIX$date-$origin"

    companion object {
        internal const val SCALE = 12
        private const val SUMMARY_PREFIX = "summary-"
        private const val ORIGIN_MARKER_PREFIX = "origin-"
    }
}
