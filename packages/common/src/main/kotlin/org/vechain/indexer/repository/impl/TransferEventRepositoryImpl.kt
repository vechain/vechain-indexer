package org.vechain.indexer.repository.impl

import org.bson.Document
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.model.TransferEventType
import org.vechain.indexer.repository.impl.SliceBuilder.buildResultsSlice

@Profile("transfers")
@Component
open class TransferEventRepositoryImpl(private val mongoTemplate: MongoTemplate) {

    fun findFungibleTokensContractsByAddress(
        address: String,
        tokenWhitelist: List<String>,
        pageable: Pageable,
    ): Slice<String> {

        val notVthoMatchOperation =
            Aggregation.match(Criteria.where(TOKEN_ADDRESS).ne(VTHO_CONTRACT_ADDRESS))
        val eventTypeMatchOperation =
            Aggregation.match(Criteria.where(EVENT_TYPE).`is`(TransferEventType.FUNGIBLE_TOKEN))
        val addressMatchOperation =
            Aggregation.match(
                Criteria.where("")
                    .orOperator(
                        Criteria.where(TO).`is`(address),
                        Criteria.where(FROM).`is`(address),
                    )
            )
        val tokenWhitelistOperation =
            Aggregation.match(Criteria.where(TOKEN_ADDRESS).`in`(tokenWhitelist))

        val groupOperation =
            Aggregation.group(TOKEN_ADDRESS)
                .first(BLOCK_NUMBER)
                .`as`(BLOCK_NUMBER)
                .first(TX_ID)
                .`as`(TX_ID)
                .first(TRANSFER_EVENT_ID)
                .`as`(TRANSFER_EVENT_ID_ALIAS)

        // Constructing the basic operations list
        val matchOperations =
            mutableListOf(notVthoMatchOperation, eventTypeMatchOperation, addressMatchOperation)

        // Only add tokenWhitelistOperation if tokenWhitelist is not empty
        if (tokenWhitelist.isNotEmpty()) {
            matchOperations.add(tokenWhitelistOperation)
        }

        // find distinct fungible token contract addresses
        val fungibleTokensContractsAggregation =
            Aggregation.newAggregation(
                matchOperations +
                    listOf(
                        groupOperation,
                        // Re-sorting is required here because the group stage does not preserve
                        // order, and
                        // post-group aliases should be used
                        Aggregation.sort(
                            Sort.by(
                                pageable.sort.getOrderFor(BLOCK_NUMBER)!!.direction,
                                BLOCK_NUMBER,
                                TX_ID,
                                TRANSFER_EVENT_ID_ALIAS,
                            )
                        ),
                        Aggregation.skip((pageable.pageNumber * pageable.pageSize).toLong()),
                        // We retrieve an additional element on purpose to detect remaining elements
                        // in the next page
                        Aggregation.limit(pageable.pageSize.toLong() + 1),
                    )
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

        return buildResultsSlice(distinctFungibleTokensContracts, pageable)
    }

    companion object {
        val TRANSFER_EVENTS_COLLECTION = IndexedTransferEvent::class.java
        val TO = IndexedTransferEvent::to.name
        val FROM = IndexedTransferEvent::from.name
        val TOKEN_ADDRESS = IndexedTransferEvent::tokenAddress.name
        val EVENT_TYPE = IndexedTransferEvent::eventType.name
        const val VTHO_CONTRACT_ADDRESS = "0x0000000000000000000000000000456e65726779"
        val BLOCK_NUMBER = IndexedTransferEvent::blockNumber.name
        val TX_ID = IndexedTransferEvent::txId.name
        val TRANSFER_EVENT_ID = IndexedTransferEvent::id.name
        const val TRANSFER_EVENT_ID_ALIAS = "transferEventId"
    }
}
