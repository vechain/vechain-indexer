package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedContract

@Profile("contracts")
@Repository
interface ContractRepo : BaseIndexedRepo<IndexedContract>, PagingAndSortingRepository<IndexedContract, String>
