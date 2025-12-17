package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository

@Profile("validator", "validator-stats")
@Repository
interface ValidatorRepository : BasePagingAndSortingIndexedRepository<Validator, String> {
    fun findByEndorser(endorser: String, pageable: Pageable): Slice<Validator>

    fun findByStatusNot(status: Status): List<Validator>

    fun findByStatus(status: Status): List<Validator>
}
