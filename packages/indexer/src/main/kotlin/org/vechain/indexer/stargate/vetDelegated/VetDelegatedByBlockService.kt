package org.vechain.indexer.stargate.vetDelegated

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.timeseries.SeriesCursor
import org.vechain.indexer.timeseries.TimeFramePeriod
import org.vechain.indexer.validator.Delegation
import org.vechain.indexer.validator.DelegationService
import org.vechain.indexer.validator.DelegationStatus
import org.vechain.indexer.validator.DelegationWriteRepository

/** Snapshots the active (ACTIVE + EXITING) delegations per level into a row per changed block. */
@Profile("delegation")
@Service
open class VetDelegatedByBlockService(
    private val repository: VetDelegatedWriteRepository,
    private val delegationRepository: DelegationWriteRepository,
) {
    private val cursor = SeriesCursor(repository::latest)

    // The committed active set by id; [save] applies each block's changes once they are written.
    private var active: MutableMap<String, Delegation>? = null

    /** A row for [block] when the total moved or a frame closed, and the re-tagged previous row. */
    open fun processBlock(block: Block, changed: List<Delegation>): List<VetDelegatedByBlock> {
        val latest = cursor.latestBefore(block)

        val overrides = changed.associateBy { it.id }
        val delegations =
            activeSet().values.filter { it.id !in overrides } + overrides.values.filter(::isActive)
        val byLevel = mutableMapOf<TokenLevel, BigInteger>()
        val nftCountByLevel = mutableMapOf<TokenLevel, Long>()
        for (d in delegations) {
            byLevel.merge(d.tokenLevel, BigInteger(d.stakedAmount), BigInteger::add)
            nftCountByLevel.merge(d.tokenLevel, 1L, Long::plus)
        }
        val total = byLevel.values.fold(BigInteger.ZERO, BigInteger::add)
        val totalNftCount = delegations.size.toLong()

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

    /** Runs inside [DelegationService.save]'s transaction, with the same [changed]. */
    open fun save(records: List<VetDelegatedByBlock>, changed: List<Delegation>) {
        if (records.isNotEmpty()) repository.save(records)
        cursor.commit(records)
        active?.let { set ->
            changed.forEach { if (isActive(it)) set[it.id] = it else set.remove(it.id) }
        }
    }

    open fun resetCache() {
        cursor.reset()
        active = null
    }

    private fun activeSet(): Map<String, Delegation> =
        active
            ?: delegationRepository
                .findActive()
                .associateByTo(mutableMapOf()) { it.id }
                .also {
                    active = it
                }

    private fun isActive(d: Delegation) =
        d.status == DelegationStatus.ACTIVE || d.status == DelegationStatus.EXITING
}
