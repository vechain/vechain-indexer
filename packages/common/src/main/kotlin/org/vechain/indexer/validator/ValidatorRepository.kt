package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("validator", "stargate-token", "history")
@Repository
interface ValidatorRepository : BaseIndexedRepository<Validator, String> {
    @Query("{ 'status': { '\$ne': ?0 } }") fun findByStatusNot(status: Status): List<Validator>

    @Query("{ 'status': ?0 }") fun findByStatus(status: Status): List<Validator>

    @Query("{ 'status': { '\$in': ?0 } }")
    fun findByStatusIn(statuses: Collection<Status>): List<Validator>

    @Query("{ 'lastMissedBlockNumber': ?0 }")
    fun findByLastMissedBlockNumber(blockNumber: Long): List<Validator>
}
