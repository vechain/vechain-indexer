package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.model.rest.ContractType

@Profile("contracts")
@Repository
interface ContractRepository : BaseIndexedRepo<IndexedContract> {
    fun findByCreatorAndType(creator: String?, contractType: ContractType?, pageable: Pageable): Page<IndexedContract>

}
