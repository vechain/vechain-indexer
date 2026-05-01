package org.vechain.indexer.safe.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.safe.SafeMembership

@Profile("safe")
interface SafeMembershipRepository : BaseIndexedRepository<SafeMembership, String> {

    /** All memberships for an owner regardless of current/past status, paginated. */
    fun findByOwner(owner: String, pageable: Pageable): Slice<SafeMembership>

    /** Active memberships for an owner (still currently an owner). */
    fun findByOwnerAndRemovedBlockIsNull(owner: String, pageable: Pageable): Slice<SafeMembership>

    /** Past memberships for an owner (no longer an owner). */
    fun findByOwnerAndRemovedBlockIsNotNull(
        owner: String,
        pageable: Pageable,
    ): Slice<SafeMembership>
}
