package org.vechain.indexer.history

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface HistoryRepository : PostgresIndexedRepository {

    /**
     * Save multiple history events.
     *
     * @param events The events to save
     */
    fun saveAll(events: List<IndexedHistoryEvent>)

    /**
     * Update is_blacklisted to true for all events with the given contract addresses.
     *
     * @param contractAddresses The contract addresses to blacklist
     */
    fun blacklist(contractAddresses: List<String>)

    /**
     * Update is_blacklisted to false for all events with the given contract addresses.
     *
     * @param contractAddresses The contract addresses to whitelist
     */
    fun whitelist(contractAddresses: List<String>)

    /**
     * Find user history events by dynamic filters for API queries.
     *
     * @param account The account address to search for (in origin, gasPayer, to, from, or owner)
     * @param eventNames Optional list of event names to filter by
     * @param searchFields Optional list of specific fields to search the account in
     * @param contractAddress Optional contract address to filter by
     * @param before Optional end timestamp (inclusive)
     * @param after Optional start timestamp (inclusive)
     * @param pageable Pagination parameters
     * @return Slice of matching history events
     */
    fun findUserHistoryByFilters(
        account: String?,
        eventNames: List<String>?,
        searchFields: List<String>?,
        contractAddress: String?,
        before: Long?,
        after: Long?,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

    /**
     * Find token history events by dynamic filters for API queries.
     *
     * @param tokenId The token ID to search for
     * @param eventNames Optional list of event names to filter by
     * @param contractAddress Optional contract address to filter by
     * @param before Optional end timestamp (inclusive)
     * @param after Optional start timestamp (inclusive)
     * @param pageable Pagination parameters
     * @return Slice of matching history events
     */
    fun findTokenIdHistoryByFilters(
        tokenId: String?,
        eventNames: List<String>?,
        contractAddress: String?,
        before: Long?,
        after: Long?,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent>

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
