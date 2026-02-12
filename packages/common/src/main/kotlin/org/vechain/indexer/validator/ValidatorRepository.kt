package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("validator", "validator-stats")
@Repository
interface ValidatorRepository : BaseIndexedRepository<Validator, String> {
    @Query("{ 'endorser': ?0 }")
    fun findByEndorser(endorser: String, pageable: Pageable): Slice<Validator>

    @Query("{ 'status': { '\$ne': ?0 } }") fun findByStatusNot(status: Status): List<Validator>

    @Query("{ 'status': ?0 }") fun findByStatus(status: Status): List<Validator>
}
