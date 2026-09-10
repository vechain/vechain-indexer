package org.vechain.indexer.nft

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import org.springframework.data.mongodb.core.query.Criteria
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.utils.SliceBuilder

// Sort precedes the lookup so the index orders rows and only the consumed page is probed.
object NftBlacklistFilter {
    const val LOOKUP_FIELD = "blacklistInfo"
    private const val BLACKLISTED_PATH = "$LOOKUP_FIELD.${NftBlacklist.IS_BLACKLISTED_FIELD}"

    fun excludeBlacklisted(localField: String): List<AggregationOperation> =
        listOf(
            Aggregation.lookup(
                IndexerNames.NFT_BLACKLIST.COLLECTION,
                localField,
                "_id",
                LOOKUP_FIELD,
            ),
            Aggregation.match(Criteria.where(BLACKLISTED_PATH).ne(true)),
        )

    fun <T : Any> findPage(
        mongoTemplate: MongoTemplate,
        criteria: Criteria,
        pageable: Pageable,
        entityClass: Class<T>,
        localField: String = "contractAddress",
    ): Slice<T> {
        val stages = mutableListOf<AggregationOperation>(Aggregation.match(criteria))
        if (pageable.sort.isSorted) stages += Aggregation.sort(pageable.sort)
        stages += excludeBlacklisted(localField)
        stages += Aggregation.skip(pageable.offset)
        stages += Aggregation.limit(pageable.pageSize + 1L)
        stages += Aggregation.project().andExclude(LOOKUP_FIELD)

        val results =
            mongoTemplate
                .aggregate(
                    Aggregation.newAggregation(stages),
                    mongoTemplate.getCollectionName(entityClass),
                    entityClass,
                )
                .mappedResults
        return SliceBuilder.buildResultsSlice(results, pageable)
    }
}
