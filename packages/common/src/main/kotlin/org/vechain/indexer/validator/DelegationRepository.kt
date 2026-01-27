package org.vechain.indexer.validator

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface DelegationRepository : PostgresIndexedRepository {
    fun saveAllVersioned(updated: List<Delegation>, existing: List<Delegation>)

    fun saveAll(delegations: List<Delegation>)

    fun findByNotify(notify: Boolean): List<Delegation>

    fun findByValidatorNextCycleInAndStatusIn(
        blockNumber: List<Long>,
        statuses: List<Status>,
    ): List<Delegation>

    fun findByValidatorIn(validators: List<String>): List<Delegation>

    fun findValidatorIdsByStatusNot(status: Status): List<String>

    fun findByValidatorAndStatusIn(
        validator: String,
        statuses: List<Status>,
        pageable: Pageable,
    ): Slice<Delegation>

    fun findByValidatorAndStatusIn(validator: String, statuses: List<Status>): List<Delegation>

    fun findByValidator(validator: String, pageable: Pageable): Slice<Delegation>

    fun findByTokenId(tokenId: String, pageable: Pageable): Slice<Delegation>

    fun findByTokenIdIn(tokenIds: List<String>): List<Delegation>

    fun findByStatusIn(statuses: Collection<Status>, pageable: Pageable): Slice<Delegation>

    fun findAll(pageable: Pageable): Slice<Delegation>

    fun aggregateDelegationCountsByValidator(): List<DelegationCountAggregateResult>

    fun aggregateDelegationCountsByValidator(
        validator: String
    ): List<DelegationCountAggregateResult>

    /**
     * Aggregate active delegations (ACTIVE + EXITING) by token level. Returns total staked amount
     * and NFT count per level.
     */
    fun aggregateActiveDelegationsByLevel(): List<DelegationLevelAggregateResult>
}

data class DelegationStatusCount(val status: String, val count: Long)

data class DelegationCountAggregateResult(val _id: String, val counts: List<DelegationStatusCount>)

data class DelegationLevelAggregateResult(
    val level: String,
    val totalWei: String,
    val nftCount: Long,
)
