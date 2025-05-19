package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.vechain.indexer.model.Address
import org.vechain.indexer.model.IndexedHistoryEvent

@Profile("history-events")
@Service
open class HistoryService(private val mongoTemplate: MongoTemplate) {
    open fun findUserHistoryByFilters(
        account: String,
        eventNames: List<String>?,
        searchFields: List<String>?,
        contractAddress: Address?,
        before: Long?,
        after: Long?,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        val query = Query()

        // Add dynamic search fields
        if (!searchFields.isNullOrEmpty()) {
            val fieldCriteria =
                Criteria()
                    .orOperator(
                        *searchFields.map { Criteria.where(it).`is`(account) }.toTypedArray()
                    )
            query.addCriteria(fieldCriteria)
        } else {
            // Default to "to", "from", "origin"
            query.addCriteria(
                Criteria()
                    .orOperator(
                        Criteria.where("to").`is`(account),
                        Criteria.where("from").`is`(account),
                        Criteria.where("origin").`is`(account),
                    )
            )
        }
        // Add contractAddress filter
        if (contractAddress != null) {
            query.addCriteria(Criteria.where("contractAddress").`is`(contractAddress.value))
        }

        // Add eventNames filter
        if (!eventNames.isNullOrEmpty()) {
            query.addCriteria(Criteria.where("eventName").`in`(eventNames))
        }

        // Add timestamp filters
        if (before != null && after != null) {
            query.addCriteria(Criteria.where("blockTimestamp").gte(after).lte(before))
        } else if (before != null) {
            query.addCriteria(Criteria.where("blockTimestamp").lte(before))
        } else if (after != null) {
            query.addCriteria(Criteria.where("blockTimestamp").gte(after))
        }

        // Ignore blacklisted events
        query.addCriteria(Criteria.where("isBlacklisted").ne(true))

        // Fetch one extra item beyond pageSize to check if another page exists
        query.with(pageable)
        // Fetch results
        val results = mongoTemplate.find(query, IndexedHistoryEvent::class.java)

        val pageSize = pageable.pageSize - 1

        // Determine if another page exists (if we got more than pageSize items)
        val hasNext = results.size > pageSize

        // Limit results to only the requested pageSize
        val limitedResults = if (hasNext) results.subList(0, pageSize) else results

        return SliceImpl(limitedResults, pageable, hasNext)
    }
}
