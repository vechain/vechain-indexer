package org.vechain.indexer.b3tr.challenges.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.b3tr.challenges.B3trChallenge
import org.vechain.indexer.b3tr.challenges.ChallengeFilter
import org.vechain.indexer.b3tr.challenges.ChallengeStatus
import org.vechain.indexer.b3tr.challenges.ChallengeVisibility

interface CustomB3trChallengeRepository {
    /**
     * Wallet-scoped query that starts from `b3tr_user_challenges` and joins to `b3tr_challenges`.
     * The filter fully defines its own status set; no additional narrowing is supported here.
     */
    fun findByFilter(
        wallet: String,
        filter: ChallengeFilter,
        pageable: Pageable,
    ): Slice<B3trChallenge>

    /**
     * Challenges visible to a wallet that it is not yet involved in. Starts from `b3tr_challenges`.
     * Backs OpenToJoin and OthersActive filters. [excludeChallengeIds] is the set of challengeIds
     * the wallet already has records for.
     */
    fun findByVisibilityAndStatusExcludingIds(
        visibility: ChallengeVisibility,
        status: ChallengeStatus,
        excludeChallengeIds: Collection<Long>,
        pageable: Pageable,
    ): Slice<B3trChallenge>

    /** Distinct `challengeId`s that the given wallet has any b3tr_user_challenges record for. */
    fun findUserChallengeIdsByWallet(wallet: String): List<Long>
}
