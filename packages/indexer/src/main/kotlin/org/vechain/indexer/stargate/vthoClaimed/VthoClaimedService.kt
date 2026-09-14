package org.vechain.indexer.stargate.vthoClaimed

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.requireOwner
import org.vechain.indexer.timeseries.SeriesCursor
import org.vechain.indexer.timeseries.TimeFramePeriod
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

/** Turns claim events into the cumulative per-block series and each (account, token)'s totals. */
@Profile("stargate", "vtho-claimed")
@Service
open class VthoClaimedService(private val repository: VthoClaimedWriteRepository) {
    private val cursor = SeriesCursor(repository::latest)
    private val legacyEventNames =
        setOf("STARGATE_CLAIM_REWARDS_BASE_LEGACY", "STARGATE_CLAIM_REWARDS_DELEGATE_LEGACY")

    /** What a batch of claim events adds to the schema, in ascending block order. */
    data class Update(
        val byBlock: List<VthoClaimedByBlock>,
        val byToken: List<VthoClaimedByToken>,
    ) {
        fun isEmpty() = byBlock.isEmpty() && byToken.isEmpty()
    }

    open fun processEvents(events: List<IndexedEvent>): Update {
        if (events.isEmpty()) return Update(emptyList(), emptyList())

        val grouped = events.groupBy { it.blockNumber }.toSortedMap()
        var prev = cursor.latestBefore(grouped.keys)
        val tokens =
            repository
                .findCurrentByAccounts(events.map { it.requireOwner() }.toSet())
                .associateBy { it.account to it.tokenId }
                .toMutableMap()

        var runningTotal = prev?.total ?: BigInteger.ZERO
        var runningLegacy = prev?.legacyRewards ?: BigInteger.ZERO
        val byBlock = mutableListOf<VthoClaimedByBlock>()
        val byToken = mutableListOf<VthoClaimedByToken>()

        for ((blockNum, blockEvents) in grouped) {
            var deltaLatest = BigInteger.ZERO
            var deltaLegacy = BigInteger.ZERO
            val touched = linkedSetOf<Pair<String, String>>()

            for (evt in blockEvents) {
                val value = evt.requireValue()
                val legacy = evt.eventType in legacyEventNames
                if (legacy) deltaLegacy += value else deltaLatest += value

                val key = evt.requireOwner() to evt.requireTokenId()
                val current = tokens[key]
                tokens[key] =
                    VthoClaimedByToken(
                        account = key.first,
                        tokenId = key.second,
                        legacyRewards =
                            (current?.legacyRewards ?: BigInteger.ZERO) +
                                (if (legacy) value else BigInteger.ZERO),
                        delegationRewards =
                            (current?.delegationRewards ?: BigInteger.ZERO) +
                                (if (legacy) BigInteger.ZERO else value),
                        blockId = evt.blockId,
                        blockNumber = blockNum,
                        blockTimestamp = evt.blockTimestamp,
                    )
                touched += key
            }
            touched.forEach { byToken += tokens.getValue(it) }

            runningTotal += deltaLatest
            runningLegacy += deltaLegacy

            val rep = blockEvents.first()
            val roll = TimeFramePeriod.roll(prev?.period, rep.blockTimestamp, deltaLatest)
            if (roll.closed.isNotEmpty() && prev != null) {
                byBlock += prev.copy(period = prev.period.copy(timeFrames = roll.closed))
            }
            val record =
                VthoClaimedByBlock(
                    blockId = rep.blockId,
                    blockNumber = blockNum,
                    blockTimestamp = rep.blockTimestamp,
                    total = runningTotal,
                    legacyRewards = runningLegacy,
                    period = roll.next,
                )
            byBlock += record
            prev = record
        }

        return Update(byBlock, byToken)
    }

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(update: Update) {
        repository.save(update.byBlock, update.byToken)
        cursor.commit(update.byBlock)
    }

    open fun resetCache() = cursor.reset()

    private fun IndexedEvent.requireValue(): BigInteger =
        params.getAsBigInteger("value")
            ?: throw IllegalStateException(
                "Event for block $blockNumber (blockId=$blockId) is missing required 'value'"
            )

    private fun IndexedEvent.requireTokenId(): String =
        params.getAsString("tokenId")
            ?: throw IllegalArgumentException("Missing 'tokenId' parameter in event")
}
