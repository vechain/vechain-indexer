package org.vechain.indexer.repos

import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.Contract

@Repository
interface ContractRepo : BaseIndexedRepo<Contract>, PagingAndSortingRepository<Contract, String>