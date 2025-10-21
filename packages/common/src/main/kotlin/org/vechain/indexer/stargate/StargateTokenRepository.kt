package org.vechain.indexer.stargate

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.validator.Status

@Profile("stargate")
@Repository
interface StargateTokenRepository : BasePagingAndSortingIndexedRepository<StargateToken, String> {
    fun findByOwner(owner: String, pageable: Pageable): Slice<StargateToken>

    fun findByManager(manager: String, pageable: Pageable): Slice<StargateToken>

    fun findByOwnerOrManager(
        owner: String,
        manager: String,
        pageable: Pageable,
    ): Slice<StargateToken>

    fun findByValidatorIdIn(validatorIds: Set<String>): List<StargateToken>

    fun findByDelegationNextPeriodAndDelegationStatusIn(
        blockNumbers: List<Long>,
        statuses: List<Status>,
    ): List<StargateToken>

    fun findAllDistinctValidatorIds(): List<String>
}
