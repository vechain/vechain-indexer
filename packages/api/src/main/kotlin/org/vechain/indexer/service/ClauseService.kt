package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.model.Address
import org.vechain.indexer.model.IndexedClause
import org.vechain.indexer.repository.ClauseRepository

@Profile("clauses")
@Service
open class ClauseService(private val clauseRepository: ClauseRepository) {

    open fun findByAddress(address: Address, pageable: Pageable): Slice<IndexedClause> {
        return clauseRepository.findByOriginOrTo(address.value, address.value, pageable)
    }
}
