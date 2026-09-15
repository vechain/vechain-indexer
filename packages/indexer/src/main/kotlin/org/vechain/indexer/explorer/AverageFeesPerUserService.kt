package org.vechain.indexer.explorer

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneOffset
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.BlockUtils
import org.vechain.indexer.utils.NumberUtils.hexToBigInteger
import org.vechain.indexer.utils.scaleDown

@Profile("explorer")
@Service
open class AverageFeesPerUserService(private val repository: ExplorerWriteRepository) {
    open fun processBlock(block: Block): AverageFeesPerUserBlockUpdate? {
        if (block.transactions.isEmpty()) {
            return null
        }

        val date = BlockUtils.getDateAtUTC(block.timestamp)
        val dayStartTimestamp = getDayStartTimestamp(block.timestamp)
        val existingSummary = repository.findCurrentFees(dayStartTimestamp)
        // A replayed block is already in the day's total; adding it again would double the fees.
        if (existingSummary != null && existingSummary.blockNumber >= block.number) return null

        val distinctOrigins = block.transactions.map { it.origin.lowercase() }.toSet()
        val known = repository.findKnownOrigins(dayStartTimestamp, distinctOrigins)
        val newOrigins =
            distinctOrigins.filterNot(known::contains).map {
                DailyActiveOrigin(dayStartTimestamp, it, block.number)
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
                newUsersInBlock = newOrigins.size.toLong(),
                existingSummary = existingSummary,
            )

        return AverageFeesPerUserBlockUpdate(
            newOrigins = newOrigins,
            updatedSummary = updatedSummary,
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
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
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

    companion object {
        internal const val SCALE = 12
    }
}

data class AverageFeesPerUserBlockUpdate(
    val newOrigins: List<DailyActiveOrigin>,
    val updatedSummary: AverageFeesPerUser,
)
