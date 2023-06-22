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
import org.vechain.indexer.model.IndexedTransaction

@Profile("transactions")
@Component
open class TransactionRepositoryImpl(
    private val mongoTemplate: MongoTemplate,
    private val countRepository: CountRepository
) {

    open fun findByOrigin(address: String, pageable: Pageable): Page<IndexedTransaction> {
        val query = Query().with(pageable)
        val criteria = Criteria.where(ORIGIN).`is`(address)

        query.addCriteria(criteria)
        val matchOperations = listOf(MatchOperation(criteria))

        val results = mongoTemplate.find(query, TRANSACTIONS_COLLECTION)
        val count = countRepository.getCount(TRANSACTIONS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    open fun findByOriginOrGasPayer(address: String, pageable: Pageable): Page<IndexedTransaction> {
        val query = Query().with(pageable)
        val criteria =
            Criteria.where("").orOperator(Criteria.where(ORIGIN).`is`(address), Criteria.where(GAS_PAYER).`is`(address))

        query.addCriteria(criteria)
        val matchOperations = listOf(MatchOperation(criteria))

        val results = mongoTemplate.find(query, TRANSACTIONS_COLLECTION)
        val count = countRepository.getCount(TRANSACTIONS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    open fun findDelegated(delegator: String, pageable: Pageable): Page<IndexedTransaction> {
        val query = Query().with(pageable)
        val notOriginCriteria = Criteria.where(ORIGIN).ne(delegator)
        val gasPayerCriteria = Criteria.where(GAS_PAYER).`is`(delegator)

        query.addCriteria(notOriginCriteria)
        query.addCriteria(gasPayerCriteria)
        val matchOperations = listOf(MatchOperation(notOriginCriteria), MatchOperation(gasPayerCriteria))

        val results = mongoTemplate.find(query, TRANSACTIONS_COLLECTION)
        val count = countRepository.getCount(TRANSACTIONS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    companion object {
        val TRANSACTIONS_COLLECTION = IndexedTransaction::class.java
        val ORIGIN = IndexedTransaction::origin.name
        val GAS_PAYER = IndexedTransaction::gasPayer.name
    }
}