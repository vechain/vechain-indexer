package org.vechain.indexer.repos

import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.Contract
import org.vechain.indexer.model.rest.ContractType

@Repository
open class ContractRepoImpl(private val mongoTemplate: MongoTemplate) {

    open fun findById(address: String): Contract? {
        return mongoTemplate.findById(address, Contract::class.java)
    }

    open fun findByCreatorAndType(
        creator: String?,
        contractType: ContractType?,
        pageable: Pageable
    ): List<Contract> {
        val query = Query().with(pageable)

        if (creator != null) query.addCriteria(Criteria.where("creator").`is`(creator))
        if (contractType != null) addTypeCriteria(contractType, query)

        return mongoTemplate.find(query, Contract::class.java)
    }

    private fun addTypeCriteria(contractType: ContractType, query: Query) {
        val criteriaKey = when (contractType) {
            ContractType.VIP180 -> "isVip180"
            ContractType.VIP181 -> "isVip181"
            ContractType.VIP210 -> "isVip210"
            ContractType.ERC20 -> "isErc20"
            ContractType.ERC721 -> "isErc721"
            ContractType.ERC1155 -> "isErc1155"
        }
        query.addCriteria(Criteria.where(criteriaKey).`is`(true))
    }
}