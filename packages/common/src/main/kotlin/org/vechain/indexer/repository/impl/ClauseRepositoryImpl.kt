package org.vechain.indexer.repository.impl

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.MatchOperation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedClause

@Profile("clauses")
@Component
open class ClauseRepositoryImpl(
    private val mongoTemplate: MongoTemplate,
    private val countRepository: CountRepository
) {

    open fun findByOriginOrTo(address: String, pageable: Pageable): Page<IndexedClause> {
        val query = Query().with(pageable)
        val criteria =
            Criteria.where("").orOperator(Criteria.where(ORIGIN).`is`(address), Criteria.where(TO).`is`(address))

        query.addCriteria(criteria)
        val matchOperations = listOf(MatchOperation(criteria))

        val results = mongoTemplate.find(query, CLAUSES_COLLECTION)
        val count = countRepository.getCount(CLAUSES_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    companion object {
        val CLAUSES_COLLECTION = IndexedClause::class.java
        val ORIGIN = IndexedClause::origin.name
        val TO = IndexedClause::to.name
    }
}