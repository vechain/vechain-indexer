package org.vechain.indexer.b3tr.action.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.b3tr.action.AppDailyActionSummary
import org.vechain.indexer.thor.HexUtils

@Profile("b3tr", "b3tr-actions", "b3tr-app-daily-action-summary")
@Repository
open class CustomAppDailyActionSummaryRepositoryImpl
@Autowired
constructor(private val mongoTemplate: MongoTemplate) : CustomAppDailyActionSummaryRepository {
    override fun findAppUserOverviewsByFilters(
        appId: String?,
        user: String?,
        startDate: String?,
        endDate: String?,
        pageable: Pageable,
    ): Slice<AppDailyActionSummary> {
        val query = Query()

        // Build criteria in a concise, null-safe way
        val combinedCriteria =
            listOfNotNull(
                    appId?.let { Criteria.where("appId").`is`(HexUtils.normalise(it)) },
                    user?.let { Criteria.where("user").`is`(HexUtils.normalise(it)) },
                    startDate?.let { Criteria.where("date").gte(it) },
                    endDate?.let { Criteria.where("date").lte(it) },
                )
                .takeIf { it.isNotEmpty() }
                ?.let { Criteria().andOperator(*it.toTypedArray()) }

        combinedCriteria?.let(query::addCriteria)

        // Let Spring apply sort/skip; we only override the limit to fetch +1 for hasNext
        query.with(pageable)
        query.limit(pageable.pageSize + 1)

        val raw = mongoTemplate.find(query, AppDailyActionSummary::class.java)

        val hasNext = raw.size > pageable.pageSize
        val content = if (hasNext) raw.dropLast(1) else raw

        return SliceImpl(content, pageable, hasNext)
    }
}
