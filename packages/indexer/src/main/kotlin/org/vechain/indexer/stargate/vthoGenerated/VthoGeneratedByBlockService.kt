package org.vechain.indexer.stargate.vthoGenerated

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.event.model.abi.InputOutput
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.timeseries.SeriesCursor
import org.vechain.indexer.timeseries.TimeFramePeriod
import org.vechain.indexer.validator.domain.ValidatorDecoder.hasAbiData

/** Accumulates the staker contract's per-block VTHO issuance into a cumulative series. */
@Profile("stargate", "vtho-generated-by-block")
@Service
open class VthoGeneratedByBlockService(private val repository: VthoGeneratedWriteRepository) {
    private val cursor = SeriesCursor(repository::latest)

    /**
     * A row for [block] when it issued VTHO, preceded by the re-tagged previous row on a rollover.
     */
    open fun processBlock(
        block: Block,
        callResponses: List<InspectionResult>,
    ): List<VthoGeneratedByBlock> {
        if (!callResponses[0].hasAbiData()) return emptyList()

        val latest = cursor.latestBefore(block)
        val issued = vthoIssued(callResponses)
        if (issued == BigInteger.ZERO) return emptyList()

        val roll = TimeFramePeriod.roll(latest?.period, block.timestamp, issued)
        val output = mutableListOf<VthoGeneratedByBlock>()
        if (roll.closed.isNotEmpty() && latest != null) {
            output += latest.copy(period = latest.period.copy(timeFrames = roll.closed))
        }
        output +=
            VthoGeneratedByBlock(
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                total = (latest?.total ?: BigInteger.ZERO) + issued,
                period = roll.next,
            )
        return output
    }

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(records: List<VthoGeneratedByBlock>) {
        if (records.isEmpty()) return
        repository.save(records)
        cursor.commit(records)
    }

    open fun resetCache() = cursor.reset()

    /**
     * The `issued` output of the staker's `issuance()` call; zero when the call carried no data.
     */
    fun vthoIssued(responses: List<InspectionResult>): BigInteger {
        if (responses.isEmpty() || !responses[0].hasAbiData()) {
            return BigInteger.ZERO
        }

        val decoded =
            FunctionReturnDecoder.decode(
                responses[0].data,
                listOf(InputOutput("uint256", "issued", "uint256")),
            )

        return decoded["issued"] as BigInteger
    }
}
