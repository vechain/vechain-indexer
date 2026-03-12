package org.vechain.indexer.explorer

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneOffset
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.explorer.repository.AverageFeesPerUserRepository
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.BlockUtils
import org.vechain.indexer.utils.NumberUtils.hexToBigInteger
import org.vechain.indexer.utils.scaleDown

@Profile("explorer", "average-fees-per-user")
@Service
open class AverageFeesPerUserService(
    private val repository: AverageFeesPerUserRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {
    open fun processBlock(block: Block): AverageFeesPerUserBlockUpdate? {
        if (block.transactions.isEmpty()) {
            return null
        }

        val date = BlockUtils.getDateAtUTC(block.timestamp)
        val dayStartTimestamp = getDayStartTimestamp(block.timestamp)
        val existingSummary = repository.findByIdOrNull(summaryId(date))

        val distinctOrigins = block.transactions.map { it.origin.lowercase() }.toSet()
        val markerIds = distinctOrigins.map { markerId(date, it) }
        val existingMarkerIds = repository.findAllById(markerIds).map { it.id }.toSet()
        val newMarkers =
            markerIds.filterNot(existingMarkerIds::contains).map { id ->
                AverageFeesPerUser(
                    id = id,
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    version = 1,
                    recordType = AverageFeesPerUserRecordType.ORIGIN_MARKER,
                    date = date,
                    origin = id.removePrefix("$ORIGIN_MARKER_PREFIX$date-"),
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

        return AverageFeesPerUserBlockUpdate(
            newMarkers = newMarkers,
            updatedSummary = updatedSummary,
            existingSummary = existingSummary,
        )
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(update: AverageFeesPerUserBlockUpdate) {
        if (update.newMarkers.isNotEmpty()) {
            repository.saveAll(update.newMarkers)
        }

        saveVersionedDocuments(
            updated = listOf(update.updatedSummary),
            existing = listOfNotNull(update.existingSummary),
            mongoTemplate = mongoTemplate,
            blockWindow = inlineVersioningProperties.blockWindow,
            maxVersions = inlineVersioningProperties.maxVersions,
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
            id = summaryId(date),
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            version = (existingSummary?.version ?: 0) + 1,
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

    internal fun summaryId(date: String): String = "$SUMMARY_PREFIX$date"

    internal fun markerId(date: String, origin: String): String =
        "$ORIGIN_MARKER_PREFIX$date-$origin"

    companion object {
        internal const val SCALE = 12
        private const val SUMMARY_PREFIX = "summary-"
        private const val ORIGIN_MARKER_PREFIX = "origin-"
    }
}

data class AverageFeesPerUserBlockUpdate(
    val newMarkers: List<AverageFeesPerUser>,
    val updatedSummary: AverageFeesPerUser,
    val existingSummary: AverageFeesPerUser?,
)
