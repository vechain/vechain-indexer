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
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.service.CountService

@Profile("transfer-events")
@Component
open class TransferEventRepositoryImpl(
    private val mongoTemplate: MongoTemplate,
    private val countService: CountService
) {

    fun findByToOrFromAndTokenAddress(
        address: String,
        tokenAddress: String,
        pageable: Pageable
    ): Page<IndexedTransferEvent> {
        val query = Query().with(pageable)
        val tokenCriteria = Criteria.where(TOKEN_ADDRESS).`is`(tokenAddress)
        val toOrFromCriteria =
            Criteria.where("").orOperator(Criteria.where(TO).`is`(address), Criteria.where(FROM).`is`(address))

        query.addCriteria(tokenCriteria)
        query.addCriteria(toOrFromCriteria)
        val matchOperations = listOf(MatchOperation(tokenCriteria), MatchOperation(toOrFromCriteria))

        val results = mongoTemplate.find(query, TRANSFERS_COLLECTION)
        val count = countService.getCount(TRANSFERS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    fun findByToOrFrom(address: String, pageable: Pageable): Page<IndexedTransferEvent> {
        val query = Query().with(pageable)
        val toOrFromCriteria =
            Criteria.where("").orOperator(Criteria.where(TO).`is`(address), Criteria.where(FROM).`is`(address))

        query.addCriteria(toOrFromCriteria)
        val matchOperations = listOf(MatchOperation(toOrFromCriteria))

        val results = mongoTemplate.find(query, TRANSFERS_COLLECTION)
        val count = countService.getCount(TRANSFERS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    fun findByTokenAddress(tokenAddress: String, pageable: Pageable): Page<IndexedTransferEvent> {
        val query = Query().with(pageable)
        val tokenCriteria = Criteria.where(TOKEN_ADDRESS).`is`(tokenAddress)

        query.addCriteria(tokenCriteria)
        val matchOperations = listOf(MatchOperation(tokenCriteria))

        val results = mongoTemplate.find(query, TRANSFERS_COLLECTION)
        val count = countService.getCount(TRANSFERS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    fun findByTo(to: String, pageable: Pageable): Page<IndexedTransferEvent> {
        val query = Query().with(pageable)
        val toCriteria = Criteria.where(TO).`is`(to)

        query.addCriteria(toCriteria)
        val matchOperations = listOf(MatchOperation(toCriteria))

        val results = mongoTemplate.find(query, TRANSFERS_COLLECTION)
        val count = countService.getCount(TRANSFERS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    fun findByToAndTokenAddress(to: String, tokenAddress: String, pageable: Pageable): Page<IndexedTransferEvent> {
        val query = Query().with(pageable)
        val toCriteria = Criteria.where(TO).`is`(to)
        val tokenAddressCriteria = Criteria.where(TOKEN_ADDRESS).`is`(tokenAddress)

        query.addCriteria(toCriteria)
        query.addCriteria(tokenAddressCriteria)
        val matchOperations = listOf(MatchOperation(toCriteria), MatchOperation(tokenAddressCriteria))

        val results = mongoTemplate.find(query, TRANSFERS_COLLECTION)
        val count = countService.getCount(TRANSFERS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    fun findByFrom(from: String, pageable: Pageable): Page<IndexedTransferEvent> {
        val query = Query().with(pageable)
        val fromCriteria = Criteria.where(FROM).`is`(from)

        query.addCriteria(fromCriteria)
        val matchOperations = listOf(MatchOperation(fromCriteria))

        val results = mongoTemplate.find(query, TRANSFERS_COLLECTION)
        val count = countService.getCount(TRANSFERS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    fun findByFromAndTokenAddress(from: String, tokenAddress: String, pageable: Pageable): Page<IndexedTransferEvent> {
        val query = Query().with(pageable)
        val fromCriteria = Criteria.where(FROM).`is`(from)
        val tokenAddressCriteria = Criteria.where(TOKEN_ADDRESS).`is`(tokenAddress)

        query.addCriteria(fromCriteria)
        query.addCriteria(tokenAddressCriteria)
        val matchOperations = listOf(MatchOperation(fromCriteria), MatchOperation(tokenAddressCriteria))

        val results = mongoTemplate.find(query, TRANSFERS_COLLECTION)
        val count = countService.getCount(TRANSFERS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    companion object {
        val TRANSFERS_COLLECTION = IndexedTransferEvent::class.java
        val TO = IndexedTransferEvent::to.name
        val FROM = IndexedTransferEvent::from.name
        val TOKEN_ADDRESS = IndexedTransferEvent::tokenAddress.name
    }
}