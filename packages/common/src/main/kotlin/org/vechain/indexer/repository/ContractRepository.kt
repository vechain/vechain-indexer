package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.model.rest.ContractType

@Profile("contracts")
@Repository
interface ContractRepository : BaseIndexedRepository<IndexedContract> {
    fun findByCreatorAndType(
        creator: String?,
        contractType: ContractType?,
        pageable: Pageable,
    ): Slice<IndexedContract>

    fun deleteAllByBlockNumber(blockNumber: Long)
}
