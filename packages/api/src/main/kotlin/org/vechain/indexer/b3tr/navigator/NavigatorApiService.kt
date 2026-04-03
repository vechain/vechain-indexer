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

    fun findNavigators(
        navigator: String? = null,
        statuses: List<NavigatorStatus>? = null,
        pageable: Pageable,
    ): Slice<Navigator> {
        val criteria = Criteria()
        navigator?.let { criteria.and(Navigator::address.name).`is`(it.lowercase()) }
        statuses?.let { criteria.and(Navigator::status.name).`in`(it.map { s -> s.name }) }
        return runQuery(criteria, pageable)
    }

    private fun runQuery(criteria: Criteria, pageable: Pageable): Slice<Navigator> {
        val query = Query(criteria).with(pageable)
        query.limit(pageable.pageSize + 1)
        val raw = mongoTemplate.find(query, Navigator::class.java)
        val hasNext = raw.size > pageable.pageSize
        val content = if (hasNext) raw.dropLast(1) else raw
        return SliceImpl(content, pageable, hasNext)
    }
}
