package org.vechain.indexer.history

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.core.query.Criteria
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
        val criteria =
            buildCriteria(account, eventNames, searchFields, contractAddress, before, after)
        return historyRepository.findNotBlacklisted(criteria, pageable)
    }

    open fun findTokenIdHistoryByFilters(
        tokenId: String?,
        eventNames: List<String>?,
        contractAddress: Address?,
        before: Long?,
        after: Long?,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        val criteria =
            buildCriteria(null, eventNames, null, contractAddress, before, after, tokenId)
        return historyRepository.findNotBlacklisted(criteria, pageable)
    }

    private fun buildCriteria(
        account: String?,
        eventNames: List<String>?,
        searchFields: List<String>?,
        contractAddress: Address?,
        before: Long?,
        after: Long?,
        tokenId: String? = null,
    ): Criteria {
        val criteria = Criteria()

        // Add dynamic search fields
        if (!searchFields.isNullOrEmpty()) {
            criteria.orOperator(
                *searchFields.map { Criteria.where(it).`is`(account) }.toTypedArray()
            )
        } else if (!account.isNullOrBlank()) {
            criteria.orOperator(
                Criteria.where(IndexedHistoryEvent::origin.name).`is`(account),
                Criteria.where(IndexedHistoryEvent::gasPayer.name).`is`(account),
                Criteria.where(IndexedHistoryEvent::to.name).`is`(account),
                Criteria.where(IndexedHistoryEvent::from.name).`is`(account),
                Criteria.where(IndexedHistoryEvent::owner.name).`is`(account),
            )
        } else {
            criteria.and(IndexedHistoryEvent::tokenId.name).`is`(tokenId)
        }

        // Add contractAddress filter
        if (contractAddress != null) {
            criteria.and(IndexedHistoryEvent::contractAddress.name).`is`(contractAddress.value)
        }

        // Add eventNames filter
        if (!eventNames.isNullOrEmpty()) {
            criteria.and(IndexedHistoryEvent::eventName.name).`in`(eventNames)
        }

        // Add timestamp filters
        if (before != null && after != null) {
            criteria.and(IndexedHistoryEvent::blockTimestamp.name).gte(after).lte(before)
        } else if (before != null) {
            criteria.and(IndexedHistoryEvent::blockTimestamp.name).lte(before)
        } else if (after != null) {
            criteria.and(IndexedHistoryEvent::blockTimestamp.name).gte(after)
        }

        return criteria
    }
}
