package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile(
    "validator-v2",
    "validator",
    "delegation-v2",
    "delegation",
    "token-reward",
    "validator-reward",
)
@Repository
interface ValidatorV2Repository : BaseIndexedRepository<ValidatorV2, String> {
    @Query("{ 'status': { '\$ne': ?0 } }") fun findByStatusNot(status: StatusV2): List<ValidatorV2>

    @Query("{ 'status': ?0 }") fun findByStatus(status: StatusV2): List<ValidatorV2>

    @Query("{ 'lastMissedBlockNumber': ?0 }")
    fun findByLastMissedBlockNumber(blockNumber: Long): List<ValidatorV2>

    @Query("{ 'lastProposedBlockNumber': ?0, '_id': { '\$in': ?1 } }")
    fun findByLastProposedBlockNumberAndIdIn(
        blockNumber: Long,
        ids: Collection<String>,
    ): List<ValidatorV2>
}
