package org.vechain.indexer.b3tr.navigator

import java.math.BigDecimal
import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service

data class NavigatorOverview(
    val activeNavigators: Long,
    val totalStaked: BigInteger,
    val totalCitizens: Long,
    val totalDelegated: BigInteger,
)

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
        return runQuery(criteria, pageable, Navigator::class.java)
    }

    fun getOverview(): NavigatorOverview {
        val activeQuery =
            Query(Criteria.where(Navigator::status.name).`is`(NavigatorStatus.ACTIVE.name))
        val activeNavigators = mongoTemplate.find(activeQuery, Navigator::class.java)

        var totalStaked = BigDecimal.ZERO
        var totalCitizens = 0L
        var totalDelegated = BigDecimal.ZERO

        for (nav in activeNavigators) {
            totalStaked += nav.stake
            totalCitizens += nav.citizenCount
            totalDelegated += nav.totalDelegated
        }

        return NavigatorOverview(
            activeNavigators = activeNavigators.size.toLong(),
            totalStaked = totalStaked.toBigInteger(),
            totalCitizens = totalCitizens,
            totalDelegated = totalDelegated.toBigInteger(),
        )
    }

    fun findCitizens(navigator: String, pageable: Pageable): Slice<NavigatorCitizen> {
        val criteria =
            Criteria.where(NavigatorCitizen::navigator.name)
                .`is`(navigator.lowercase())
                .and(NavigatorCitizen::active.name)
                .`is`(true)
        return runQuery(criteria, pageable, NavigatorCitizen::class.java)
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
