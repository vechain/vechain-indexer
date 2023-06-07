package org.vechain.indexer.repos

import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.model.rest.ContractType

@Repository
open class ContractRepoImpl(private val mongoTemplate: MongoTemplate) {

    companion object {
        val CREATOR = IndexedContract::creator.name
    }

    open fun findById(address: String): IndexedContract? {
        return mongoTemplate.findById(address, IndexedContract::class.java)
    }

    open fun findByCreatorAndType(
        creator: String?,
        contractType: ContractType?,
        pageable: Pageable
    ): List<IndexedContract> {
        val query = Query().with(pageable)

        if (creator != null) query.addCriteria(Criteria.where(CREATOR).`is`(creator))
        if (contractType != null) addTypeCriteria(contractType, query)

        return mongoTemplate.find(query, IndexedContract::class.java)
    }

    private fun addTypeCriteria(contractType: ContractType, query: Query) {
        val criteriaKey = when (contractType) {
            ContractType.VIP180 -> IndexedContract::isVip180.name
            ContractType.VIP181 -> IndexedContract::isVip181.name
            ContractType.VIP210 -> IndexedContract::isVip210.name
            ContractType.ERC20 -> IndexedContract::isErc20.name
            ContractType.ERC721 -> IndexedContract::isErc721.name
            ContractType.ERC1155 -> IndexedContract::isErc1155.name
        }
        query.addCriteria(Criteria.where(criteriaKey).`is`(true))
    }
}