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
import org.vechain.indexer.explorer.repository.AverageFeesPerUserOriginMarkerRepository
import org.vechain.indexer.explorer.repository.AverageFeesPerUserRepository
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.BlockUtils
import org.vechain.indexer.utils.CacheUtils
import org.vechain.indexer.utils.NumberUtils.hexToBigInteger
import org.vechain.indexer.utils.scaleDown

@Profile("explorer", "average-fees-per-user")
@Service
open class AverageFeesPerUserService(
    private val repository: AverageFeesPerUserRepository,
    private val originMarkerRepository: AverageFeesPerUserOriginMarkerRepository,
    private val inlineVersioningProperties: InlineVersioningProperties,
    private val mongoTemplate: MongoTemplate,
) {
    @Volatile private var lastProcessedDailyMetric: AverageFeesPerUser? = null

    open fun processBlock(
        block: Block
    ): Triple<
        List<AverageFeesPerUser>,
        List<AverageFeesPerUser>,
        List<AverageFeesPerUserOriginMarker>,
    > {
        if (block.transactions.isEmpty()) {
            return Triple(emptyList(), emptyList(), emptyList())
        }

        val date = BlockUtils.getDateAtUTC(block.timestamp)
        val dayStartTimestamp = getDayStartTimestamp(block.timestamp)
        val existingSummary = getPreviousSummary(date, block.number)

        val distinctOrigins = block.transactions.map { it.origin.lowercase() }.toSet()
        val markerIds = distinctOrigins.map { markerId(date, it) }
        val existingMarkerIds = originMarkerRepository.findAllById(markerIds).map { it.id }.toSet()
        val newMarkers =
            markerIds.filterNot(existingMarkerIds::contains).map { id ->
                val origin = id.removePrefix("$date-")
                AverageFeesPerUserOriginMarker(
                    id = id,
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
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

        return Triple(listOf(updatedSummary), listOfNotNull(existingSummary), newMarkers)
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(
        updated: List<AverageFeesPerUser>,
        existing: List<AverageFeesPerUser>,
        newMarkers: List<AverageFeesPerUserOriginMarker>,
    ) {
        if (updated.isEmpty()) return

        if (newMarkers.isNotEmpty()) {
            originMarkerRepository.saveAll(newMarkers)
        }

        saveVersionedDocuments(
            updated,
            existing,
            mongoTemplate,
            inlineVersioningProperties.blockWindow,
            inlineVersioningProperties.maxVersions,
        )

        CacheUtils.updateAfterCommit(
            updated.last(),
            setter = { lastProcessedDailyMetric = it },
            clear = ::clearProcessingState,
        )
    }

    open fun clearProcessingState() {
        lastProcessedDailyMetric = null
    }

    open fun deleteMarkersFromBlock(blockNumber: Long) {
        originMarkerRepository.deleteAllByBlockNumberGreaterThanEqual(blockNumber)
    }

    internal fun getPreviousSummary(date: String, blockNumber: Long): AverageFeesPerUser? {
        val cached = lastProcessedDailyMetric
        if (cached != null && cached.id == date && cached.blockNumber < blockNumber) {
            return cached
        }

        return repository.findByIdOrNull(date)
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
            id = date,
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            date = date,
            dayStartTimestamp = dayStartTimestamp,
            totalFeesPaid = totalFeesPaid,
            dailyActiveUsers = dailyActiveUsers,
            averageFeesPerUser = calculateAverage(totalFeesPaid, dailyActiveUsers),
            version = (existingSummary?.version ?: 0) + 1,
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

    internal fun markerId(date: String, origin: String): String = "$date-$origin"

    companion object {
        internal const val SCALE = 12
    }
}
