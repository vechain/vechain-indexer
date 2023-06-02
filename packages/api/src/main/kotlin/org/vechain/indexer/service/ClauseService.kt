package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.IndexedClause
import org.vechain.indexer.repos.ClauseRepo
import org.vechain.indexer.utils.HexUtil

@Profile("clauses")
@Service
open class ClauseService(private val clauseRepo: ClauseRepo) {

    open fun findByAddress(address: String, pageable: Pageable): Page<IndexedClause> {
        val addressNorm = HexUtil.normalise(address)
        return clauseRepo.findByOriginOrTo(addressNorm, addressNorm, pageable)
    }

}
