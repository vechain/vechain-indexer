package org.vechain.indexer.transfer

import org.bson.Document
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Component
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS
import org.vechain.indexer.utils.SliceBuilder

@Profile("transfers")
@Component
open class TransferEventRepositoryImpl(private val mongoTemplate: MongoTemplate) {

    fun findFungibleTokensContractsByAddress(
        address: String,
        tokenWhitelist: List<String>,
        pageable: Pageable,
    ): Slice<String> {

        // Build the match criteria
        val matchCriteria =
            Criteria()
                .and(EVENT_TYPE)
                .`is`(TransferEventType.FUNGIBLE_TOKEN)
                .orOperator(Criteria.where(TO).`is`(address), Criteria.where(FROM).`is`(address))

        // Handle tokenAddress filters: combine $ne and $in if whitelist is provided
        if (tokenWhitelist.isNotEmpty()) {
            // Filter by whitelist (which implicitly excludes VTHO if not in the list)
            matchCriteria.and(TOKEN_ADDRESS).`in`(tokenWhitelist)
        } else {
            // No whitelist, just exclude VTHO
            matchCriteria.and(TOKEN_ADDRESS).ne(VTHO_CONTRACT_ADDRESS)
        }

        val sortDirection = pageable.sort.getOrderFor(BLOCK_NUMBER)!!.direction

        // Optimized pipeline: sort BEFORE grouping to use $first/$last meaningfully
        val fungibleTokensContractsAggregation =
            Aggregation.newAggregation(
                // Single combined match operation
                Aggregation.match(matchCriteria),
                // Sort BEFORE grouping so $first gives us the earliest/latest based on sort
                // direction
                Aggregation.sort(Sort.by(sortDirection, BLOCK_NUMBER, TX_ID, TRANSFER_EVENT_ID)),
                // Group by token address and keep the first occurrence (respects sort order)
                Aggregation.group(TOKEN_ADDRESS)
                    .first(BLOCK_NUMBER)
                    .`as`(BLOCK_NUMBER)
                    .first(TX_ID)
                    .`as`(TX_ID)
                    .first(TRANSFER_EVENT_ID)
                    .`as`(TRANSFER_EVENT_ID_ALIAS),
                // Sort again by the grouped fields for consistent pagination
                Aggregation.sort(
                    Sort.by(sortDirection, BLOCK_NUMBER, TX_ID, TRANSFER_EVENT_ID_ALIAS)
                ),
                Aggregation.skip((pageable.pageNumber * pageable.pageSize).toLong()),
                // Retrieve an additional element to detect remaining pages
                Aggregation.limit(pageable.pageSize.toLong() + 1),
            )

        val distinctFungibleTokensContracts =
            mongoTemplate
                .aggregate(
                    fungibleTokensContractsAggregation,
                    TRANSFER_EVENTS_COLLECTION,
                    Document::class.java,
                )
                .mappedResults
                .map { it["_id"] as String }

        return SliceBuilder.buildResultsSlice(distinctFungibleTokensContracts, pageable)
    }

    companion object {
        val TRANSFER_EVENTS_COLLECTION = IndexedTransferEvent::class.java
        val TO = IndexedTransferEvent::to.name
        val FROM = IndexedTransferEvent::from.name
        val TOKEN_ADDRESS = IndexedTransferEvent::tokenAddress.name
        val EVENT_TYPE = IndexedTransferEvent::eventType.name
        val BLOCK_NUMBER = IndexedTransferEvent::blockNumber.name
        val TX_ID = IndexedTransferEvent::txId.name
        val TRANSFER_EVENT_ID = IndexedTransferEvent::id.name
        const val TRANSFER_EVENT_ID_ALIAS = "transferEventId"
    }
}
