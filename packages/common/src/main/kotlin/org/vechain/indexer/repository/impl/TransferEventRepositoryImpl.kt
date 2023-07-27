package org.vechain.indexer.repository.impl

import org.bson.Document
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.MatchOperation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.model.TransferEventType

@Profile("transfer-events")
@Component
open class TransferEventRepositoryImpl(
    private val mongoTemplate: MongoTemplate,
    private val countRepository: CountRepository
) {

    fun findByToOrFromAndTokenAddress(
        address: String,
        tokenAddress: String,
        pageable: Pageable
    ): Page<IndexedTransferEvent> {
        val query = Query().with(pageable)
        val tokenCriteria = Criteria.where(TOKEN_ADDRESS).`is`(tokenAddress)
        val toOrFromCriteria =
            Criteria.where("")
                .orOperator(Criteria.where(TO).`is`(address), Criteria.where(FROM).`is`(address))

        query.addCriteria(tokenCriteria)
        query.addCriteria(toOrFromCriteria)
        val matchOperations =
            listOf(MatchOperation(tokenCriteria), MatchOperation(toOrFromCriteria))

        val results = mongoTemplate.find(query, TRANSFER_EVENTS_COLLECTION)
        val count = countRepository.getCount(TRANSFER_EVENTS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    fun findByToOrFrom(address: String, pageable: Pageable): Page<IndexedTransferEvent> {
        val query = Query().with(pageable)
        val toOrFromCriteria =
            Criteria.where("")
                .orOperator(Criteria.where(TO).`is`(address), Criteria.where(FROM).`is`(address))

        query.addCriteria(toOrFromCriteria)
        val matchOperations = listOf(MatchOperation(toOrFromCriteria))

        val results = mongoTemplate.find(query, TRANSFER_EVENTS_COLLECTION)
        val count = countRepository.getCount(TRANSFER_EVENTS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    fun findByTokenAddress(tokenAddress: String, pageable: Pageable): Page<IndexedTransferEvent> {
        val query = Query().with(pageable)
        val tokenCriteria = Criteria.where(TOKEN_ADDRESS).`is`(tokenAddress)

        query.addCriteria(tokenCriteria)
        val matchOperations = listOf(MatchOperation(tokenCriteria))

        val results = mongoTemplate.find(query, TRANSFER_EVENTS_COLLECTION)
        val count = countRepository.getCount(TRANSFER_EVENTS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    fun findByTo(to: String, pageable: Pageable): Page<IndexedTransferEvent> {
        val query = Query().with(pageable)
        val toCriteria = Criteria.where(TO).`is`(to)

        query.addCriteria(toCriteria)
        val matchOperations = listOf(MatchOperation(toCriteria))

        val results = mongoTemplate.find(query, TRANSFER_EVENTS_COLLECTION)
        val count = countRepository.getCount(TRANSFER_EVENTS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    fun findByToAndTokenAddress(
        to: String,
        tokenAddress: String,
        pageable: Pageable
    ): Page<IndexedTransferEvent> {
        val query = Query().with(pageable)
        val toCriteria = Criteria.where(TO).`is`(to)
        val tokenAddressCriteria = Criteria.where(TOKEN_ADDRESS).`is`(tokenAddress)

        query.addCriteria(toCriteria)
        query.addCriteria(tokenAddressCriteria)
        val matchOperations =
            listOf(MatchOperation(toCriteria), MatchOperation(tokenAddressCriteria))

        val results = mongoTemplate.find(query, TRANSFER_EVENTS_COLLECTION)
        val count = countRepository.getCount(TRANSFER_EVENTS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    fun findByFrom(from: String, pageable: Pageable): Page<IndexedTransferEvent> {
        val query = Query().with(pageable)
        val fromCriteria = Criteria.where(FROM).`is`(from)

        query.addCriteria(fromCriteria)
        val matchOperations = listOf(MatchOperation(fromCriteria))

        val results = mongoTemplate.find(query, TRANSFER_EVENTS_COLLECTION)
        val count = countRepository.getCount(TRANSFER_EVENTS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    fun findByFromAndTokenAddress(
        from: String,
        tokenAddress: String,
        pageable: Pageable
    ): Page<IndexedTransferEvent> {
        val query = Query().with(pageable)
        val fromCriteria = Criteria.where(FROM).`is`(from)
        val tokenAddressCriteria = Criteria.where(TOKEN_ADDRESS).`is`(tokenAddress)

        query.addCriteria(fromCriteria)
        query.addCriteria(tokenAddressCriteria)
        val matchOperations =
            listOf(MatchOperation(fromCriteria), MatchOperation(tokenAddressCriteria))

        val results = mongoTemplate.find(query, TRANSFER_EVENTS_COLLECTION)
        val count = countRepository.getCount(TRANSFER_EVENTS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    fun findFungibleTokensContractsByAddress(address: String, pageable: Pageable): Page<String> {
        val matchAggregation =
            Aggregation.match(
                Criteria.where(EVENT_TYPE)
                    .`is`(TransferEventType.FUNGIBLE_TOKEN)
                    .andOperator(
                        Criteria.where("")
                            .orOperator(
                                Criteria.where(TO).`is`(address),
                                Criteria.where(FROM).`is`(address)
                            )
                    )
            )

        val groupAggregation = Aggregation.group(TOKEN_ADDRESS)

        // count distinct fungible token contract addresses
        val fungibleTokensContractsCount =
            countRepository.getCount(
                TRANSFER_EVENTS_COLLECTION,
                listOf(matchAggregation),
                groupAggregation
            )

        // find distinct fungible token contract addresses
        val fungibleTokensContractsAggregation =
            Aggregation.newAggregation(
                matchAggregation,
                groupAggregation,
                Aggregation.sort(pageable.sort),
                Aggregation.skip((pageable.pageNumber * pageable.pageSize).toLong()),
                Aggregation.limit(pageable.pageSize.toLong())
            )
        val distinctFungibleTokensContracts =
            mongoTemplate
                .aggregate(
                    fungibleTokensContractsAggregation,
                    TRANSFER_EVENTS_COLLECTION,
                    Document::class.java
                )
                .mappedResults
                .map { it["_id"] as String }

        return PageImpl(distinctFungibleTokensContracts, pageable, fungibleTokensContractsCount)
    }

    companion object {
        val TRANSFER_EVENTS_COLLECTION = IndexedTransferEvent::class.java
        val TO = IndexedTransferEvent::to.name
        val FROM = IndexedTransferEvent::from.name
        val TOKEN_ADDRESS = IndexedTransferEvent::tokenAddress.name
        val EVENT_TYPE = IndexedTransferEvent::eventType.name
    }
}
