package org.vechain.indexer.repos

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.Contract

@Repository
interface ContractRepo : BaseIndexedRepo<Contract>, PagingAndSortingRepository<Contract, String> {
    fun findAllByCreator(creator: String, pageable: Pageable): Page<Contract>
}