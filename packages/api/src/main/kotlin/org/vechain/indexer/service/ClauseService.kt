package org.vechain.indexer.service

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.WrappedClause
import org.vechain.indexer.repos.ClauseRepo
import org.vechain.indexer.utils.HexUtil

@Service
open class ClauseService(private val clauseRepo: ClauseRepo) {

    open fun findByAddress(address: String, pageable: Pageable): List<WrappedClause> {
        val addressNorm = HexUtil.normalise(address)
        return clauseRepo.findByOriginOrTo(addressNorm, addressNorm, pageable)
    }

}