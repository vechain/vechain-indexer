package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("validator-v2", "delegation-v2")
@Repository
interface ValidatorV2Repository : BaseIndexedRepository<ValidatorV2, String> {
    @Query("{ 'status': { '\$ne': ?0 } }") fun findByStatusNot(status: StatusV2): List<ValidatorV2>

    @Query("{ 'status': ?0 }") fun findByStatus(status: StatusV2): List<ValidatorV2>
}
