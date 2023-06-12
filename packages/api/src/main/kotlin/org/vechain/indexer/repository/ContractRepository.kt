package org.vechain.indexer.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.MatchOperation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.CriteriaDefinition
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.model.rest.ContractType

@Repository
open class ContractRepository(
    private val mongoTemplate: MongoTemplate,
    private val countRepository: CountRepository
) {

    companion object {
        val CONTRACTS_COLLECTION = IndexedContract::class.java
        val CREATOR = IndexedContract::creator.name
    }

    open fun findById(address: String): IndexedContract? {
        return mongoTemplate.findById(address, CONTRACTS_COLLECTION)
    }

    open fun findByCreatorAndType(
        creator: String?,
        contractType: ContractType?,
        pageable: Pageable
    ): Page<IndexedContract> {
        val query = Query().with(pageable)
        val matchOperations = mutableListOf<MatchOperation>()

        if (creator != null) {
            query.addCriteria(Criteria.where(CREATOR).`is`(creator))
            matchOperations.add(MatchOperation(Criteria.where(CREATOR).`is`(creator)))
        }
        if (contractType != null) {
            val contractTypeCriteria = buildTypeCriteria(contractType)
            query.addCriteria(contractTypeCriteria)
            matchOperations.add(MatchOperation(contractTypeCriteria))
        }

        val results = mongoTemplate.find(query, CONTRACTS_COLLECTION)
        val count = countRepository.getCount(CONTRACTS_COLLECTION, matchOperations)

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