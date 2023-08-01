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
import org.vechain.indexer.model.IndexedNFT

@Profile("nft-events")
@Component
open class NFTRepositoryImpl(
    private val mongoTemplate: MongoTemplate,
    private val countRepository: CountRepository
) {

    open fun findByOwner(owner: String, pageable: Pageable): Page<IndexedNFT> {
        val query = Query().with(pageable)
        val criteria = Criteria.where(OWNER).`is`(owner)

        query.addCriteria(criteria)
        val matchOperations = listOf(MatchOperation(criteria))

        val results = mongoTemplate.find(query, NFTS_COLLECTION)
        val count = countRepository.getCount(NFTS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    open fun findByOwnerAndContractAddress(
        owner: String,
        contractAddress: String,
        pageable: Pageable
    ): Page<IndexedNFT> {
        val query = Query().with(pageable)
        val criteria = Criteria.where(OWNER).`is`(owner).and(CONTRACT_ADDRESS).`is`(contractAddress)

        query.addCriteria(criteria)
        val matchOperations = listOf(MatchOperation(criteria))

        val results = mongoTemplate.find(query, NFTS_COLLECTION)
        val count = countRepository.getCount(NFTS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    open fun findContractsByNFTOwner(owner: String, pageable: Pageable): Page<String> {
        val matchAggregation = Aggregation.match(Criteria.where(OWNER).`is`(owner))
        val groupAggregation = Aggregation.group(CONTRACT_ADDRESS)

        // count distinct contracts
        val distinctCount =
            countRepository.getCount(NFTS_COLLECTION, listOf(matchAggregation), groupAggregation)

        // find distinct contracts
        val contractsAggregation =
            Aggregation.newAggregation(
                matchAggregation,
                Aggregation.sort(pageable.sort),
                groupAggregation,
                Aggregation.skip((pageable.pageNumber * pageable.pageSize).toLong()),
                Aggregation.limit(pageable.pageSize.toLong())
            )
        val distinctContracts =
            mongoTemplate
                .aggregate(contractsAggregation, IndexedNFT::class.java, Document::class.java)
                .mappedResults
                .map { it["_id"] as String }

        return PageImpl(distinctContracts, pageable, distinctCount)
    }

    companion object {
        val NFTS_COLLECTION = IndexedNFT::class.java
        val OWNER = IndexedNFT::owner.name
        val CONTRACT_ADDRESS = IndexedNFT::contractAddress.name
    }
}
