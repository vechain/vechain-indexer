package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.IndexedClause
import org.vechain.indexer.repository.ClauseRepo
import org.vechain.indexer.utils.HexUtils

@Profile("clauses")
@Service
open class ClauseService(private val clauseRepo: ClauseRepo) {

    open fun findByAddress(address: String, pageable: Pageable): List<IndexedClause> {
        val addressNorm = HexUtils.normalise(address)
        return clauseRepo.findByOriginOrTo(addressNorm, addressNorm, pageable)
    }

}
