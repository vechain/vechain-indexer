package org.vechain.indexer.contracts.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.contracts.Contract
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface ContractRepository : PostgresIndexedRepository {
    fun saveAllVersioned(updated: List<Contract>, existing: List<Contract>)

    fun findById(id: String): Contract?

    fun findByMaster(master: String, pageable: Pageable): Slice<Contract>
}
