package org.vechain.indexer.stargate.token

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface StargateTokenRepository : PostgresIndexedRepository {
    fun saveAllVersioned(updated: List<StargateToken>, existing: List<StargateToken>)

    fun findById(id: String): StargateToken?

    fun findAllById(ids: Collection<String>): List<StargateToken>

    fun saveAll(tokens: Collection<StargateToken>)

    fun findAll(pageable: Pageable): Slice<StargateToken>

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
        statuses: List<String>,
    ): List<StargateToken>

    fun findAllDistinctValidatorIds(): List<String?>
}
