package org.vechain.indexer.repository.impl

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.MatchOperation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.CriteriaDefinition
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.model.rest.ContractType
import org.vechain.indexer.service.CountService

@Profile("contracts")
@Component
open class ContractRepositoryImpl(
    private val mongoTemplate: MongoTemplate,
    private val countService: CountService
) {

    companion object {
        val CONTRACTS_COLLECTION = IndexedContract::class.java
        val CREATOR = IndexedContract::creator.name
    }

    open fun findByCreatorAndType(
        creator: String?,
        contractType: ContractType?,
        pageable: Pageable
    ): Page<IndexedContract> {
        val query = Query().with(pageable)
        val matchOperations = mutableListOf<MatchOperation>()

        if (creator != null) {
            val contractCreatorCriteria = Criteria.where(CREATOR).`is`(creator)
            query.addCriteria(contractCreatorCriteria)
            matchOperations.add(MatchOperation(contractCreatorCriteria))
        }
        if (contractType != null) {
            val contractTypeCriteria = buildTypeCriteria(contractType)
            query.addCriteria(contractTypeCriteria)
            matchOperations.add(MatchOperation(contractTypeCriteria))
        }

        val results = mongoTemplate.find(query, CONTRACTS_COLLECTION)
        val count = countService.getCount(CONTRACTS_COLLECTION, matchOperations)

        return PageImpl(results, pageable, count)
    }

    private fun buildTypeCriteria(contractType: ContractType): CriteriaDefinition {
        val criteriaKey = when (contractType) {
            ContractType.VIP180 -> IndexedContract::isVip180.name
            ContractType.VIP181 -> IndexedContract::isVip181.name
            ContractType.VIP210 -> IndexedContract::isVip210.name
            ContractType.ERC20 -> IndexedContract::isErc20.name
            ContractType.ERC721 -> IndexedContract::isErc721.name
            ContractType.ERC1155 -> IndexedContract::isErc1155.name
        }
        return Criteria.where(criteriaKey).`is`(true)
    }
}