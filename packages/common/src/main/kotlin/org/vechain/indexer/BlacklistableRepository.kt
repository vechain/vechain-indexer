package org.vechain.indexer

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.vechain.indexer.nft.IndexedNft
import org.vechain.indexer.nft.NftBlacklist
import org.vechain.indexer.nft.NftBlacklistRepository
import org.vechain.indexer.utils.SliceBuilder

abstract class BlacklistableRepository<T : Any>(
    private val mongoTemplate: MongoTemplate,
    private val repo: NftBlacklistRepository,
    private val entityClass: Class<T>,
) {
    open fun findNotBlacklisted(criteria: Criteria, pageable: Pageable): Slice<T> {

        // Get blacklisted NFTs
        val blacklisted: List<NftBlacklist> = repo.findByIsBlacklisted(true)

        // Build blacklist predicate only when there are blacklisted addresses
        val blacklistedAddresses = blacklisted.map { it.contractAddress }
        val combinedCriteria =
            if (blacklistedAddresses.isEmpty()) {
                // Nothing to exclude
                criteria
            } else {
                // Avoid "second 'contractAddress' expression" by wrapping with $and
                Criteria()
                    .andOperator(
                        criteria,
                        Criteria.where(IndexedNft::contractAddress.name).nin(blacklistedAddresses),
                    )
            }
        val matchOperation = Aggregation.match(combinedCriteria)

        val aggregation =
            Aggregation.newAggregation(
                matchOperation,
                Aggregation.sort(pageable.sort),
                Aggregation.skip((pageable.pageNumber * pageable.pageSize).toLong()),
                Aggregation.limit(pageable.pageSize.toLong() + 1),
            )
        return SliceBuilder.buildResultsSlice(
            mongoTemplate.aggregate(aggregation, entityClass, entityClass).mappedResults,
            pageable,
        )
    }
}
