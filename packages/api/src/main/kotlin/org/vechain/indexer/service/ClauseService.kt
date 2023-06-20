package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.aggregation.MatchOperation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Service
import org.vechain.indexer.model.IndexedClause
import org.vechain.indexer.repository.ClauseRepo
import org.vechain.indexer.repository.CountRepository
import org.vechain.indexer.utils.HexUtils

@Profile("clauses")
@Service
open class ClauseService(private val clauseRepo: ClauseRepo, private val countRepository: CountRepository) {

    open fun findByAddress(address: String, pageable: Pageable): Page<IndexedClause> {
        val addressNorm = HexUtils.normalise(address)

        val slice = clauseRepo.findByOriginOrTo(addressNorm, addressNorm, pageable)
        val count = countRepository.getCount(
            CLAUSES_COLLECTION,
            listOf(
                MatchOperation(
                    Criteria.where("")
                        .orOperator(Criteria.where(ORIGIN).`is`(addressNorm), Criteria.where(TO).`is`(addressNorm))
                )
            )
        )

        return PageImpl(slice.content, pageable, count)
    }

    companion object {
        val CLAUSES_COLLECTION = IndexedClause::class.java
        val ORIGIN = IndexedClause::origin.name
        val TO = IndexedClause::to.name
    }

}
