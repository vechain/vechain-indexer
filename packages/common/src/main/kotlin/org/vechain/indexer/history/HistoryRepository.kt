package org.vechain.indexer.history

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository

@Profile("history", "b3tr")
@Repository
interface HistoryRepository : BasePagingAndSortingIndexedRepository<IndexedHistoryEvent, String> {
    fun findNotBlacklisted(criteria: Criteria, pageable: Pageable): Slice<IndexedHistoryEvent>

    fun findAllByToAndEventName(
        to: String,
        eventName: String,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    fun findAllByToAndEventNameAndBlockTimestampAfter(
        to: String,
        eventName: String,
        start: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    fun findAllByToAndEventNameAndBlockTimestampBefore(
        to: String,
        eventName: String,
        end: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    fun findAllByToAndEventNameAndBlockTimestampBetween(
        to: String,
        eventName: String,
        start: Long,
        end: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    fun findAllByToAndAppIdAndEventNameAndBlockTimestampBetween(
        to: String,
        appId: String,
        eventName: String,
        start: Long,
        end: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    fun findAllByToAndAppIdAndEventNameAndBlockTimestampAfter(
        to: String,
        appId: String,
        eventName: String,
        start: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    fun findAllByToAndAppIdAndEventNameAndBlockTimestampBefore(
        to: String,
        appId: String,
        eventName: String,
        end: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    fun findAllByToAndAppIdAndEventName(
        to: String,
        appId: String,
        eventName: String,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    fun findAllByAppIdAndEventNameAndBlockTimestampBetween(
        appId: String,
        eventName: String,
        start: Long,
        end: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    fun findAllByAppIdAndEventNameAndBlockTimestampAfter(
        appId: String,
        eventName: String,
        start: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    fun findAllByAppIdAndEventNameAndBlockTimestampBefore(
        appId: String,
        eventName: String,
        end: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    fun findAllByAppIdAndEventName(
        appId: String,
        eventName: String,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>
}
