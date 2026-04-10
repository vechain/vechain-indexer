package org.vechain.indexer.b3tr.richlist

import java.math.BigDecimal
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.b3tr.balance.B3trBalance

@Profile("b3tr", "b3tr-balance")
@Service
open class B3trRichlistCountService(private val mongoTemplate: MongoTemplate) {

    private val collection = IndexerNames.B3TR_BALANCE.COLLECTION

    @Cacheable(value = ["b3tr_richlist_total_holders"], key = "#scope.name()", sync = true)
    open fun getPositiveHolderCount(scope: RichlistScope): Long {
        return countBalancesGreaterThan(scope, BigDecimal.ZERO)
    }

    open fun countBalancesGreaterThan(scope: RichlistScope, threshold: BigDecimal): Long {
        return mongoTemplate.count(
            Query.query(Criteria.where(scope.sortField).gt(threshold)),
            B3trBalance::class.java,
            collection,
        )
    }
}
