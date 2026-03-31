package org.vechain.indexer.b3tr.navigator

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service

@Profile("b3tr")
@Service
open class NavigatorApiService(private val mongoTemplate: MongoTemplate) {

    fun findEvents(
        navigator: String? = null,
        eventType: String? = null,
        after: Long? = null,
        before: Long? = null,
        pageable: Pageable,
    ): Slice<NavigatorEvent> {
        val criteria = Criteria()
        navigator?.let { criteria.and(NavigatorEvent::navigator.name).`is`(it.lowercase()) }
        eventType?.let { criteria.and(NavigatorEvent::eventType.name).`is`(it) }
        addTimestampCriteria(criteria, NavigatorEvent::blockTimestamp.name, after, before)
        return runQuery(criteria, pageable, NavigatorEvent::class.java)
    }

    fun findDelegations(
        citizen: String? = null,
        navigator: String? = null,
        after: Long? = null,
        before: Long? = null,
        pageable: Pageable,
    ): Slice<NavigatorDelegation> {
        val criteria = Criteria()
        citizen?.let { criteria.and(NavigatorDelegation::citizen.name).`is`(it.lowercase()) }
        navigator?.let { criteria.and(NavigatorDelegation::navigator.name).`is`(it.lowercase()) }
        addTimestampCriteria(criteria, NavigatorDelegation::blockTimestamp.name, after, before)
        return runQuery(criteria, pageable, NavigatorDelegation::class.java)
    }

    fun findFees(
        navigator: String? = null,
        after: Long? = null,
        before: Long? = null,
        pageable: Pageable,
    ): Slice<NavigatorFee> {
        val criteria = Criteria()
        navigator?.let { criteria.and(NavigatorFee::navigator.name).`is`(it.lowercase()) }
        addTimestampCriteria(criteria, NavigatorFee::blockTimestamp.name, after, before)
        return runQuery(criteria, pageable, NavigatorFee::class.java)
    }

    private fun addTimestampCriteria(
        criteria: Criteria,
        field: String,
        after: Long?,
        before: Long?,
    ) {
        if (after != null && before != null) {
            criteria.and(field).gte(after).lte(before)
        } else if (before != null) {
            criteria.and(field).lte(before)
        } else if (after != null) {
            criteria.and(field).gte(after)
        }
    }

    private fun <T> runQuery(criteria: Criteria, pageable: Pageable, clazz: Class<T>): Slice<T> {
        val query = Query(criteria).with(pageable)
        query.limit(pageable.pageSize + 1)
        val raw = mongoTemplate.find(query, clazz)
        val hasNext = raw.size > pageable.pageSize
        val content = if (hasNext) raw.dropLast(1) else raw
        return SliceImpl(content, pageable, hasNext)
    }
}
