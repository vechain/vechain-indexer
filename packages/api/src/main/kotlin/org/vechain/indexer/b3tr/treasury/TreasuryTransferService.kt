package org.vechain.indexer.b3tr.treasury

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
open class TreasuryTransferService(private val mongoTemplate: MongoTemplate) {

    fun find(
        category: TreasuryTransferCategory? = null,
        after: Long? = null,
        before: Long? = null,
        pageable: Pageable,
    ): Slice<TreasuryTransfer> {
        val criteria = buildCriteria(category = category, after = after, before = before)
        return runQuery(criteria, pageable)
    }

    private fun buildCriteria(
        category: TreasuryTransferCategory? = null,
        after: Long? = null,
        before: Long? = null,
    ): Criteria {
        val criteria = Criteria()

        if (category != null) {
            criteria.and(TreasuryTransfer::category.name).`is`(category)
        }

        if (after != null && before != null) {
            criteria.and(TreasuryTransfer::blockTimestamp.name).gte(after).lte(before)
        } else if (before != null) {
            criteria.and(TreasuryTransfer::blockTimestamp.name).lte(before)
        } else if (after != null) {
            criteria.and(TreasuryTransfer::blockTimestamp.name).gte(after)
        }

        return criteria
    }

    private fun runQuery(criteria: Criteria, pageable: Pageable): Slice<TreasuryTransfer> {
        val query = Query(criteria).with(pageable)
        query.limit(pageable.pageSize + 1)
        val raw = mongoTemplate.find(query, TreasuryTransfer::class.java)

        val hasNext = raw.size > pageable.pageSize
        val content = if (hasNext) raw.dropLast(1) else raw

        return SliceImpl(content, pageable, hasNext)
    }
}
