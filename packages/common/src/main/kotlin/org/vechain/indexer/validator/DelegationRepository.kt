package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository

@Profile("delegation")
@Repository
interface DelegationRepository : BasePagingAndSortingIndexedRepository<Delegation, String> {
    fun findByNotify(notify: Boolean): List<Delegation>

    fun findByValidatorNextCycleAndStatusIn(
        blockNumber: Long,
        statuses: List<Status>,
    ): List<Delegation>

    fun findByValidatorIn(validators: List<String>): List<Delegation>
}
