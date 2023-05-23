package org.vechain.indexer.repos

import org.springframework.context.annotation.Profile
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.Contract

@Profile("contracts")
@Repository
interface ContractRepo : BaseIndexedRepo<Contract>, PagingAndSortingRepository<Contract, String>
