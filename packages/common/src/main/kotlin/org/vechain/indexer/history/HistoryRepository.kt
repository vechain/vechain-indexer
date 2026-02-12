package org.vechain.indexer.history

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("history", "b3tr")
@Repository
interface HistoryRepository : BaseIndexedRepository<IndexedHistoryEvent, String> {
    @Query("{ 'to': ?0, 'eventName': ?1 }")
    fun findAllByToAndEventName(
        to: String,
        eventName: String,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    @Query("{ 'to': ?0, 'eventName': ?1, 'blockTimestamp': { '\$gt': ?2 } }")
    fun findAllByToAndEventNameAndBlockTimestampAfter(
        to: String,
        eventName: String,
        start: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    @Query("{ 'to': ?0, 'eventName': ?1, 'blockTimestamp': { '\$lt': ?2 } }")
    fun findAllByToAndEventNameAndBlockTimestampBefore(
        to: String,
        eventName: String,
        end: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    @Query("{ 'to': ?0, 'eventName': ?1, 'blockTimestamp': { '\$gte': ?2, '\$lte': ?3 } }")
    fun findAllByToAndEventNameAndBlockTimestampBetween(
        to: String,
        eventName: String,
        start: Long,
        end: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    @Query(
        "{ 'to': ?0, 'appId': ?1, 'eventName': ?2, 'blockTimestamp': { '\$gte': ?3, '\$lte': ?4 } }"
    )
    fun findAllByToAndAppIdAndEventNameAndBlockTimestampBetween(
        to: String,
        appId: String,
        eventName: String,
        start: Long,
        end: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    @Query("{ 'to': ?0, 'appId': ?1, 'eventName': ?2, 'blockTimestamp': { '\$gt': ?3 } }")
    fun findAllByToAndAppIdAndEventNameAndBlockTimestampAfter(
        to: String,
        appId: String,
        eventName: String,
        start: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    @Query("{ 'to': ?0, 'appId': ?1, 'eventName': ?2, 'blockTimestamp': { '\$lt': ?3 } }")
    fun findAllByToAndAppIdAndEventNameAndBlockTimestampBefore(
        to: String,
        appId: String,
        eventName: String,
        end: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    @Query("{ 'to': ?0, 'appId': ?1, 'eventName': ?2 }")
    fun findAllByToAndAppIdAndEventName(
        to: String,
        appId: String,
        eventName: String,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    @Query("{ 'appId': ?0, 'eventName': ?1, 'blockTimestamp': { '\$gte': ?2, '\$lte': ?3 } }")
    fun findAllByAppIdAndEventNameAndBlockTimestampBetween(
        appId: String,
        eventName: String,
        start: Long,
        end: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    @Query("{ 'appId': ?0, 'eventName': ?1, 'blockTimestamp': { '\$gt': ?2 } }")
    fun findAllByAppIdAndEventNameAndBlockTimestampAfter(
        appId: String,
        eventName: String,
        start: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    @Query("{ 'appId': ?0, 'eventName': ?1, 'blockTimestamp': { '\$lt': ?2 } }")
    fun findAllByAppIdAndEventNameAndBlockTimestampBefore(
        appId: String,
        eventName: String,
        end: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    @Query("{ 'appId': ?0, 'eventName': ?1 }")
    fun findAllByAppIdAndEventName(
        appId: String,
        eventName: String,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>
}
