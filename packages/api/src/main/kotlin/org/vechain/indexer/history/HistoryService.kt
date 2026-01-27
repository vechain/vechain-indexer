package org.vechain.indexer.history

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.Address

@Profile("history")
@Service
open class HistoryService(private val historyRepository: HistoryRepository) {
    open fun findUserHistoryByFilters(
        account: String,
        eventNames: List<String>?,
        searchFields: List<String>?,
        contractAddress: Address?,
        before: Long?,
        after: Long?,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        return historyRepository.findUserHistoryByFilters(
            account = account,
            eventNames = eventNames,
            searchFields = searchFields,
            contractAddress = contractAddress?.value,
            before = before,
            after = after,
            pageable = pageable,
        )
    }

    open fun findTokenIdHistoryByFilters(
        tokenId: String?,
        eventNames: List<String>?,
        contractAddress: Address?,
        before: Long?,
        after: Long?,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        return historyRepository.findTokenIdHistoryByFilters(
            tokenId = tokenId,
            eventNames = eventNames,
            contractAddress = contractAddress?.value,
            before = before,
            after = after,
            pageable = pageable,
        )
    }
}
