package org.vechain.indexer.b3tr.action

import org.springframework.cache.annotation.CachePut
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.b3tr.shared.EntityType

@Profile("b3tr", "b3tr-actions")
@Service
open class GlobalOverviewCountService(private val mongoTemplate: MongoTemplate) {

    private val collection = IndexerNames.USER_ALL_TIME_ACTION_SUMMARY.COLLECTION

    @CachePut(value = ["user_all_time_action_countByEntityType"], key = "#entityType")
    open fun refreshCountByEntityType(entityType: EntityType): Long {
        return mongoTemplate.count(
            Query.query(Criteria.where("entityType").`is`(entityType)),
            UserAllTimeActionSummary::class.java,
            collection,
        )
    }
}
