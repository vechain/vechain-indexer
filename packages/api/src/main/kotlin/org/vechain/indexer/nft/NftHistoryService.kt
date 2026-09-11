package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.history.HistoryEventName
import org.vechain.indexer.history.HistoryReadRepository
import org.vechain.indexer.history.IndexedHistoryEvent
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.BigIntegerUtils
import org.vechain.indexer.utils.PaginationUtils.offsetSlice

@Profile("nfts")
@Service
open class NftHistoryService(private val repository: HistoryReadRepository) {

    open fun findTokenHistory(
        contractAddress: Address,
        tokenId: String,
        eventNames: List<String>?,
        before: Long?,
        after: Long?,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        val normalizedTokenId = BigIntegerUtils.fromHexOrDecimal(tokenId).toString(10)
        val requestedEventNames = eventNames?.takeIf { it.isNotEmpty() } ?: DEFAULT_EVENT_NAMES
        return offsetSlice(pageable, IndexedHistoryEvent::blockTimestamp.name) {
            offset,
            limit,
            direction ->
            repository.findTokenHistory(
                contractAddress.value,
                normalizedTokenId,
                requestedEventNames,
                after,
                before,
                offset,
                limit,
                direction,
            )
        }
    }

    companion object {
        val ALLOWED_EVENT_NAMES: Set<HistoryEventName> =
            linkedSetOf(HistoryEventName.TRANSFER_NFT, HistoryEventName.NFT_SALE)

        val DEFAULT_EVENT_NAMES: List<String> = ALLOWED_EVENT_NAMES.map { it.name }
    }
}
