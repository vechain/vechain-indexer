package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository

@Profile("delegation")
@Repository
interface DelegationRepository : BasePagingAndSortingIndexedRepository<Delegation, String> {
    fun findByNotify(notify: Boolean): List<Delegation>

    fun findByDelegationId(delegationId: String): Delegation?

    fun findByValidatorNextCycleBlockAndStatus(blockNumber: Long, status: Status): List<Delegation>

    fun findByValidatorId(validatorId: String): List<Delegation>
}
