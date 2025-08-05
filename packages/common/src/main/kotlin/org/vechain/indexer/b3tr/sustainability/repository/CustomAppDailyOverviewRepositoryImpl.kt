package org.vechain.indexer.b3tr.sustainability.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.b3tr.sustainability.AppDailyOverview
import org.vechain.indexer.thor.HexUtils

@Repository
open class CustomAppDailyOverviewRepositoryImpl
@Autowired
constructor(private val mongoTemplate: MongoTemplate) : CustomAppDailyOverviewRepository {
    override fun findAppUserOverviewsByFilters(
        appId: String?,
        user: String?,
        startDate: String?,
        endDate: String?,
        pageable: Pageable,
    ): Slice<AppDailyOverview> {
        val query = Query()

        // Add criteria dynamically
        val criteria = mutableListOf<Criteria>()

        if (appId != null) {
            criteria.add(Criteria.where("appId").`is`(HexUtils.normalise(appId)))
        }

        if (user != null) {
            criteria.add(Criteria.where("user").`is`(HexUtils.normalise(user)))
        }

        // Apply date range filter
        if (startDate != null) {
            criteria.add(Criteria.where("date").gte(startDate))
        }

        if (endDate != null) {
            criteria.add(Criteria.where("date").lte(endDate))
        }

        // Apply criteria to the query
        if (criteria.isNotEmpty()) {
            query.addCriteria(Criteria().andOperator(*criteria.toTypedArray()))
        }

        // Pagination
        // Sort and skip are still applied from 'pageable'
        query.with(pageable.sort)
        // Manually set skip
        query.skip((pageable.pageNumber * pageable.pageSize).toLong())
        // Fetch pageSize+1 items so we can detect 'hasNext'
        query.limit(pageable.pageSize + 1)

        val rawAppDailyOverviews = mongoTemplate.find(query, AppDailyOverview::class.java)

        // If we got more than pageSize items, there's a next page
        val hasNext = rawAppDailyOverviews.size > pageable.pageSize
        // The actual slice content is up to pageSize items only
        val content = if (hasNext) rawAppDailyOverviews.dropLast(1) else rawAppDailyOverviews

        return SliceImpl(content, pageable, hasNext)
    }
}
