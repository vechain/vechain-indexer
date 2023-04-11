package org.vechain.indexer.repos

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.Contract

@Repository
interface ContractRepo : PagingAndSortingRepository<Contract, String>, CrudRepository<Contract, String> {

    @Aggregation(pipeline = ["{ '\$sort': { 'blockNumber': -1 } }", "{ '\$limit': 1 }"])
    fun getMaxBlockNumber(): List<Contract>

    fun findAllByCreator(creator: String, pageable: Pageable): Page<Contract>
}