package org.vechain.indexer.history

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.history.HistoryReadRepository.SearchField
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils.offsetSlice

@Profile("history")
@Service
open class HistoryService(private val repository: HistoryReadRepository) {

    open fun findUserHistoryByFilters(
        account: String,
        eventNames: List<String>?,
        searchFields: List<String>?,
        contractAddress: Address?,
        before: Long?,
        after: Long?,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> =
        offsetSlice(pageable, IndexedHistoryEvent::blockTimestamp.name) { offset, limit, direction
            ->
            val fields = searchFields.orEmpty().mapNotNull(SearchField::of)
            if (fields.isEmpty()) {
                repository.findByAccount(
                    account,
                    eventNames,
                    contractAddress?.value,
                    after,
                    before,
                    offset,
                    limit,
                    direction,
                )
            } else {
                repository.findBySearchFields(
                    account,
                    fields,
                    eventNames,
                    contractAddress?.value,
                    after,
                    before,
                    offset,
                    limit,
                    direction,
                )
            }
        }
}
