package org.vechain.indexer.service

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.WrappedClause
import org.vechain.indexer.repos.ClauseRepo

@Service
open class ClauseService(private val clauseRepo: ClauseRepo) {

    open fun findAll(pageable: Pageable): List<WrappedClause> {
        return clauseRepo.findAllBy(pageable)
    }

}