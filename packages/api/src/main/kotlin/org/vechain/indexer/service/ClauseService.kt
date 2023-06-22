package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.IndexedClause
import org.vechain.indexer.repository.ClauseRepository
import org.vechain.indexer.utils.HexUtils

@Profile("clauses")
@Service
open class ClauseService(private val clauseRepository: ClauseRepository) {

    open fun findByAddress(address: String, pageable: Pageable): Page<IndexedClause> {
        return clauseRepository.findByOriginOrTo(HexUtils.normalise(address), pageable)
    }

}
