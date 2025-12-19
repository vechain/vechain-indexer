package org.vechain.indexer.contracts.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.contracts.Contract

@Profile("contracts", "contract")
interface ContractRepository : BaseIndexedRepository<Contract, String> {
    fun findByMaster(master: String, pageable: Pageable): Slice<Contract>
}
