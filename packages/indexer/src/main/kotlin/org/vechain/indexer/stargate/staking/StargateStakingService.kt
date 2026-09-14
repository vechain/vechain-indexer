package org.vechain.indexer.stargate.staking

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.requireLevel
import org.vechain.indexer.stargate.requireOwner
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.timeseries.SeriesCursor
import org.vechain.indexer.timeseries.TimeFramePeriod
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger

/**
 * One pass over Stargate stake and unstake events: the VET-staked series, each owner's NFT balance,
 * and the holders series derived from the balances that crossed zero.
 */
@Profile("stargate", "stargate-staking")
@Service
open class StargateStakingService(private val repository: StargateStakingWriteRepository) {
    private val stakedCursor = SeriesCursor(repository::latestVetStaked)
    private val holdersCursor = SeriesCursor(repository::latestNftHolders)

    /** What a batch of stake events adds to the schema, in ascending block order. */
    data class Update(
        val vetStaked: List<VetStakedByBlock>,
        val nftHolders: List<NftHoldersByBlock>,
        val ownerBalances: List<NftOwnerBalance>,
    ) {
        fun isEmpty() = vetStaked.isEmpty() && nftHolders.isEmpty() && ownerBalances.isEmpty()
    }

    open fun processEvents(events: List<IndexedEvent>): Update {
        if (events.isEmpty()) return Update(emptyList(), emptyList(), emptyList())

        val grouped = events.groupBy { it.blockNumber }.toSortedMap()
        var prevStaked = stakedCursor.latestBefore(grouped.keys)
        var prevHolders = holdersCursor.latestBefore(grouped.keys)
        val balances =
            repository
                .latestBalancesBefore(events.map { it.requireOwner() }.toSet(), grouped.firstKey())
                .associateBy { it.owner }
                .toMutableMap()

        var stakedTotal = prevStaked?.total ?: BigInteger.ZERO
        val stakedByLevel = prevStaked?.byLevel?.toMutableMap() ?: mutableMapOf()
        var nftCount = prevStaked?.totalNftCount ?: 0L
        val nftCountByLevel = prevStaked?.nftCountByLevel?.toMutableMap() ?: mutableMapOf()
        var holders = prevHolders?.total ?: 0L
        val holdersByLevel =
            prevHolders?.byLevel?.toMutableMap() ?: mutableMapOf<TokenLevel, Long>()

        val vetStaked = mutableListOf<VetStakedByBlock>()
        val nftHolders = mutableListOf<NftHoldersByBlock>()
        val ownerBalances = mutableListOf<NftOwnerBalance>()

        for ((blockNum, blockEvents) in grouped) {
            var stakedDelta = BigInteger.ZERO
            var holdersDelta = 0L
            val touched = linkedSetOf<String>()

            for (evt in blockEvents) {
                val amount = evt.requireValue()
                val level = evt.requireLevel()
                val owner = evt.requireOwner()
                val sign =
                    when (evt.eventType) {
                        "STARGATE_STAKE" -> 1
                        "STARGATE_UNSTAKE" -> -1
                        else ->
                            throw IllegalArgumentException("Unknown eventType: ${evt.eventType}")
                    }

                val signed = if (sign > 0) amount else amount.negate()
                stakedTotal += signed
                stakedByLevel[level] = (stakedByLevel[level] ?: BigInteger.ZERO) + signed
                stakedDelta += signed
                nftCount += sign
                nftCountByLevel[level] = (nftCountByLevel[level] ?: 0L) + sign

                val before = balances[owner]
                val beforeTotal = before?.total ?: 0L
                val beforeLevel = before?.byLevel?.get(level) ?: 0L
                balances[owner] =
                    NftOwnerBalance(
                        owner = owner,
                        total = beforeTotal + sign,
                        byLevel = before?.byLevel.orEmpty() + (level to beforeLevel + sign),
                        blockId = evt.blockId,
                        blockNumber = blockNum,
                        blockTimestamp = evt.blockTimestamp,
                    )
                touched += owner

                // An owner joins the holders when a balance leaves zero and leaves when it reaches
                // it.
                if (crossesZero(beforeTotal, sign)) {
                    holders += sign
                    holdersDelta += sign
                }
                if (crossesZero(beforeLevel, sign)) {
                    holdersByLevel[level] = (holdersByLevel[level] ?: 0L) + sign
                }
            }
            touched.forEach { ownerBalances += balances.getValue(it) }

            val rep = blockEvents.first()
            val stakedRoll =
                TimeFramePeriod.roll(prevStaked?.period, rep.blockTimestamp, stakedDelta)
            if (stakedRoll.closed.isNotEmpty() && prevStaked != null) {
                vetStaked +=
                    prevStaked.copy(period = prevStaked.period.copy(timeFrames = stakedRoll.closed))
            }
            val staked =
                VetStakedByBlock(
                    blockId = rep.blockId,
                    blockNumber = blockNum,
                    blockTimestamp = rep.blockTimestamp,
                    total = stakedTotal,
                    byLevel = stakedByLevel.toMap(),
                    totalNftCount = nftCount,
                    nftCountByLevel = nftCountByLevel.toMap(),
                    period = stakedRoll.next,
                )
            vetStaked += staked
            prevStaked = staked

            val holdersRoll =
                TimeFramePeriod.roll(
                    prevHolders?.period,
                    rep.blockTimestamp,
                    BigInteger.valueOf(holdersDelta),
                )
            if (holdersRoll.closed.isNotEmpty() && prevHolders != null) {
                nftHolders +=
                    prevHolders.copy(
                        period = prevHolders.period.copy(timeFrames = holdersRoll.closed)
                    )
            }
            val holdersRecord =
                NftHoldersByBlock(
                    blockId = rep.blockId,
                    blockNumber = blockNum,
                    blockTimestamp = rep.blockTimestamp,
                    total = holders,
                    byLevel = holdersByLevel.toMap(),
                    period = holdersRoll.next,
                )
            nftHolders += holdersRecord
            prevHolders = holdersRecord
        }

        return Update(vetStaked, nftHolders, ownerBalances)
    }

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(update: Update) {
        repository.save(update.vetStaked, update.nftHolders, update.ownerBalances)
        stakedCursor.commit(update.vetStaked)
        holdersCursor.commit(update.nftHolders)
    }

    open fun resetCache() {
        stakedCursor.reset()
        holdersCursor.reset()
    }

    private fun crossesZero(before: Long, sign: Int): Boolean =
        if (sign > 0) before == 0L else before == 1L

    private fun IndexedEvent.requireValue(): BigInteger =
        params.getAsBigInteger("value")
            ?: throw IllegalStateException(
                "Event for block $blockNumber (blockId=$blockId) is missing required 'value'"
            )
}
