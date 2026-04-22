package org.vechain.indexer.b3tr.challenges.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.b3tr.challenges.B3trChallenge
import org.vechain.indexer.b3tr.challenges.ChallengeStatus

interface CustomB3trChallengeRepository {
    fun findByWalletAndStatus(
        wallet: String,
        status: ChallengeStatus?,
        pageable: Pageable,
    ): Slice<B3trChallenge>
}
