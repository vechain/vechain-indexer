package org.vechain.indexer.stargate

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.history.HistoryEventName
import org.vechain.indexer.history.HistoryReadRepository
import org.vechain.indexer.history.IndexedHistoryEvent
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.BigIntegerUtils
import org.vechain.indexer.utils.PaginationUtils.offsetSlice

@Profile("stargate")
@Service
class StargateTokenHistoryService(
    private val repository: HistoryReadRepository,
    @Value(
        "\${business-event.substitutions.STARGATE_NFT_CONTRACT:\${STARGATE_NFT_CONTRACT:0x1856c533ac2d94340aaa8544d35a5c1d4a21dee7}}"
    )
    stargateNftContract: String,
) {
    private val stargateNftContract = HexUtils.normalise(stargateNftContract)
    private val protocolEventNames = PROTOCOL_EVENT_NAMES.map { it.name }
    private val nftScopedEventNames = NFT_SCOPED_EVENT_NAMES.map { it.name }

    fun findTokenHistory(
        tokenId: String,
        eventNames: List<String>?,
        before: Long?,
        after: Long?,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        val normalizedTokenId = BigIntegerUtils.fromHexOrDecimal(tokenId).toString(10)
        return offsetSlice(pageable, IndexedHistoryEvent::blockTimestamp.name) {
            offset,
            limit,
            direction ->
            repository.findStargateTokenHistory(
                normalizedTokenId,
                eventNames?.takeIf { it.isNotEmpty() },
                protocolEventNames,
                nftScopedEventNames,
                stargateNftContract,
                after,
                before,
                offset,
                limit,
                direction,
            )
        }
    }

    companion object {
        val PROTOCOL_EVENT_NAMES: Set<HistoryEventName> =
            HistoryEventName.entries.filterTo(linkedSetOf()) { it.name.startsWith("STARGATE_") }

        val NFT_SCOPED_EVENT_NAMES: Set<HistoryEventName> =
            linkedSetOf(
                HistoryEventName.TRANSFER_NFT,
                HistoryEventName.NFT_SALE,
                HistoryEventName.VEVOTE_VOTE_CAST,
            )

        val ALLOWED_EVENT_NAMES: Set<HistoryEventName> =
            PROTOCOL_EVENT_NAMES + NFT_SCOPED_EVENT_NAMES
    }
}
