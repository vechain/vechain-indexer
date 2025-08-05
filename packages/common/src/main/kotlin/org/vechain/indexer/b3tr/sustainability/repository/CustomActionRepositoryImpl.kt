package org.vechain.indexer.b3tr.sustainability.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.b3tr.sustainability.Action
import org.vechain.indexer.thor.HexUtils

@Repository
open class CustomActionRepositoryImpl
@Autowired
constructor(private val mongoTemplate: MongoTemplate) : CustomActionRepository {
    override fun findActionsByFilters(
        appId: String?,
        wallet: String?,
        startTimestamp: Long?,
        endTimestamp: Long?,
        pageable: Pageable,
    ): Slice<Action> {
        val query = Query()

        // Add criteria dynamically
        val criteria = mutableListOf<Criteria>()

        if (appId != null) {
            criteria.add(Criteria.where("appId").`is`(HexUtils.normalise(appId)))
        }

        if (wallet != null) {
            criteria.add(Criteria.where("receiver").`is`(HexUtils.normalise(wallet)))
        }

        // Apply blockTimestamp range filter
        if (startTimestamp != null) {
            criteria.add(Criteria.where("blockTimestamp").gte(startTimestamp))
        }

        if (endTimestamp != null) {
            criteria.add(Criteria.where("blockTimestamp").lte(endTimestamp))
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

        val rawActions = mongoTemplate.find(query, Action::class.java)

        // If we got more than pageSize items, there's a next page
        val hasNext = rawActions.size > pageable.pageSize
        // The actual slice content is up to pageSize items only
        val content = if (hasNext) rawActions.dropLast(1) else rawActions

        return SliceImpl(content, pageable, hasNext)
    }
}
