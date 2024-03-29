package org.vechain.indexer.repository.impl

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.*
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.MatchOperation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.CriteriaDefinition
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.model.rest.ContractType
import org.vechain.indexer.repository.impl.SliceBuilder.buildResultsSlice

@Profile("contracts")
@Component
open class ContractRepositoryImpl(
    private val mongoTemplate: MongoTemplate,
) {

    open fun findByCreatorAndType(
        creator: String?,
        contractType: ContractType?,
        pageable: Pageable,
    ): Slice<IndexedContract> {

        val matchOperations = mutableListOf<MatchOperation>()

        if (creator != null) {
            matchOperations.add(MatchOperation(Criteria.where(CREATOR).`is`(creator)))
        }

        if (contractType != null) {
            matchOperations.add(MatchOperation(buildTypeCriteria(contractType)))
        }

        val contractsAggregation =
            Aggregation.newAggregation(
                matchOperations +
                    listOf(
                        Aggregation.sort(
                            Sort.by(
                                pageable.sort.getOrderFor(BLOCK_NUMBER)!!.direction,
                                BLOCK_NUMBER,
                                TX_ID,
                                CONTRACT_ID,
                            )
                        ),
                        Aggregation.skip((pageable.pageNumber * pageable.pageSize).toLong()),
                        // We retrieve an additional element on purpose to detect remaining elements
                        // in the next page
                        Aggregation.limit(pageable.pageSize.toLong() + 1)
                    )
            )

        val contracts =
            mongoTemplate
                .aggregate(contractsAggregation, CONTRACTS_COLLECTION, CONTRACTS_COLLECTION)
                .mappedResults

        return buildResultsSlice(contracts, pageable)
    }

    private fun buildTypeCriteria(contractType: ContractType): CriteriaDefinition {
        val criteriaKey =
            when (contractType) {
                ContractType.VIP180 -> IndexedContract::isVip180.name
                ContractType.VIP181 -> IndexedContract::isVip181.name
                ContractType.VIP210 -> IndexedContract::isVip210.name
                ContractType.ERC20 -> IndexedContract::isErc20.name
                ContractType.ERC721 -> IndexedContract::isErc721.name
                ContractType.ERC1155 -> IndexedContract::isErc1155.name
            }
        return Criteria.where(criteriaKey).`is`(true)
    }

    companion object {
        val CONTRACTS_COLLECTION = IndexedContract::class.java
        val CREATOR = IndexedContract::creator.name
        val BLOCK_NUMBER = IndexedContract::blockNumber.name
        val TX_ID = IndexedContract::txId.name
        val CONTRACT_ID = IndexedContract::address.name
    }
}
