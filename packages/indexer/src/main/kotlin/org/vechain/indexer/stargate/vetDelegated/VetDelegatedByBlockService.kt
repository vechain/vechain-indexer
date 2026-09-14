package org.vechain.indexer.stargate.vetDelegated

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.timeseries.SeriesCursor
import org.vechain.indexer.timeseries.TimeFramePeriod
import org.vechain.indexer.validator.DelegationReadRepository

/** Snapshots the active (ACTIVE + EXITING) delegations per level into a row per changed block. */
@Profile("stargate", "vet-delegated-by-block")
@Service
open class VetDelegatedByBlockService(
    private val repository: VetDelegatedWriteRepository,
    private val delegationRepository: DelegationReadRepository,
) {
    private val cursor = SeriesCursor(repository::latest)

    /** A row for [block] when the total moved or a frame closed, and the re-tagged previous row. */
    open fun processBlock(block: Block): List<VetDelegatedByBlock> {
        val latest = cursor.latestBefore(block)

        var total = BigInteger.ZERO
        val byLevel = mutableMapOf<TokenLevel, BigInteger>()
        var totalNftCount = 0L
        val nftCountByLevel = mutableMapOf<TokenLevel, Long>()
        for (result in delegationRepository.aggregateActiveDelegationsByLevel()) {
            val level = TokenLevel.valueOf(result.level)
            val amount = BigInteger(result.totalWei)
            byLevel[level] = amount
            nftCountByLevel[level] = result.nftCount
            total += amount
            totalNftCount += result.nftCount
        }

        val delta = total - (latest?.total ?: BigInteger.ZERO)
        val roll = TimeFramePeriod.roll(latest?.period, block.timestamp, delta)

        if (delta == BigInteger.ZERO && latest != null && roll.closed.isEmpty()) {
            cursor.advance(
                latest.copy(
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                )
            )
            return emptyList()
        }

        val output = mutableListOf<VetDelegatedByBlock>()
        if (roll.closed.isNotEmpty() && latest != null) {
            output += latest.copy(period = latest.period.copy(timeFrames = roll.closed))
        }
        output +=
            VetDelegatedByBlock(
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                total = total,
                byLevel = byLevel.toMap(),
                totalNftCount = totalNftCount,
                nftCountByLevel = nftCountByLevel.toMap(),
                period = roll.next,
            )
        return output
    }

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun saveRecords(records: List<VetDelegatedByBlock>) {
        repository.save(records)
        cursor.commit(records)
    }

    open fun resetCache() = cursor.reset()
}
