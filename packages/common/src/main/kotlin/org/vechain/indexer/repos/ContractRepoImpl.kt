package org.vechain.indexer.repos

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.model.rest.ContractType

@Repository
open class ContractRepoImpl(private val mongoTemplate: MongoTemplate) {

    open fun findById(address: String): IndexedContract? {
        return mongoTemplate.findById(address, IndexedContract::class.java)
    }

    open fun findByCreatorAndType(
        creator: String?,
        contractType: ContractType?,
        pageable: Pageable
    ): Page<IndexedContract> {
        val query = Query().with(pageable)

        if (creator != null) query.addCriteria(Criteria.where("creator").`is`(creator))
        if (contractType != null) addTypeCriteria(contractType, query)

        val count = mongoTemplate.count(query, IndexedContract::class.java)
        val list = mongoTemplate.find(query, IndexedContract::class.java)

        return PageImpl(list, pageable, count)
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