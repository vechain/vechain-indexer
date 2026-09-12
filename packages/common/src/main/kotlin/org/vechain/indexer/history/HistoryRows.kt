package org.vechain.indexer.history

import java.math.BigDecimal

/** One `history.event` row; strings on the model become typed columns, see [HistoryRowMapping]. */
class HistoryEventRow(
    val id: ByteArray,
    val blockNumber: Long,
    val blockId: ByteArray,
    val blockTimestamp: Long,
    val txId: ByteArray,
    val eventName: HistoryEventName,
    val origin: ByteArray?,
    val gasPayer: ByteArray?,
    val reverted: Boolean?,
    val contractAddress: ByteArray?,
    val tokenId: BigDecimal?,
    val to: ByteArray?,
    val from: ByteArray?,
    val owner: ByteArray?,
    val value: BigDecimal?,
    val appId: ByteArray?,
    val roundId: Long?,
    val proposalId: BigDecimal?,
    val support: Short?,
    val votePower: BigDecimal?,
    val voteWeight: BigDecimal?,
    val reason: String?,
    val oldLevel: Short?,
    val newLevel: Short?,
    val levelId: Short?,
    val inputToken: ByteArray?,
    val outputToken: ByteArray?,
    val inputValue: BigDecimal?,
    val outputValue: BigDecimal?,
    val vetGeneratedVthoRewards: BigDecimal?,
    val delegationRewards: BigDecimal?,
    val migrated: Boolean?,
    val autorenew: Boolean?,
    val validator: ByteArray?,
    val delegationId: BigDecimal?,
    val periodClaimed: Long?,
    val boostedBlocks: BigDecimal?,
    val proof: String?,
    val appVotes: String?,
    val tokenIds: List<BigDecimal>?,
    val lifecycleStatus: Short?,
    val lifecycleNextCycle: Long?,
    val lifecycleCycleLength: Long?,
    val lifecycleForceExit: Boolean?,
    val lifecycleOrder: Int?,
)

class HistoryEventAddressRow(
    val address: ByteArray,
    val blockTimestamp: Long,
    val eventId: ByteArray,
    val eventName: HistoryEventName,
    val blockNumber: Long,
)

class HistoryRows(val event: HistoryEventRow, val addresses: List<HistoryEventAddressRow>)
