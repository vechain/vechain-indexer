package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository

@Profile("validator")
@Repository
interface ValidatorRepository : BasePagingAndSortingIndexedRepository<Validator, String> {
    @Query("{ '\$or': [ { '_id': { '\$in': ?0 } }, { 'delegationIdList': { '\$in': ?1 } } ] }")
    fun findByIdsOrDelegations(
        validatorIds: List<String>,
        delegationIds: List<String>,
    ): List<Validator>

    fun findByEndorser(endorser: String, pageable: Pageable): Slice<Validator>
}
