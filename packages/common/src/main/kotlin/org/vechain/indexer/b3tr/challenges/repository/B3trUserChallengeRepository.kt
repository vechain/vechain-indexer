package org.vechain.indexer.b3tr.challenges.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.b3tr.challenges.B3trUserChallenge

@Profile("b3tr", "b3tr-challenges")
@Repository
interface B3trUserChallengeRepository : BaseIndexedRepository<B3trUserChallenge, String> {
    @Query("{ 'wallet': ?0, 'isRelevant': true }")
    fun findByWallet(wallet: String, pageable: Pageable): Slice<B3trUserChallenge>

    @Query("{ 'wallet': ?0, 'isActionable': true }")
    fun findByWalletAndIsActionableTrue(
        wallet: String,
        pageable: Pageable,
    ): Slice<B3trUserChallenge>

    @Query("{ 'wallet': ?0, 'isParticipating': true }")
    fun findByWalletAndIsParticipatingTrue(
        wallet: String,
        pageable: Pageable,
    ): Slice<B3trUserChallenge>

    @Query("{ 'wallet': ?0, 'isHistorical': true }")
    fun findByWalletAndIsHistoricalTrue(
        wallet: String,
        pageable: Pageable,
    ): Slice<B3trUserChallenge>

    @Query("{ 'wallet': ?0, 'challengeId': ?1, 'isRelevant': true }")
    fun findByWalletAndChallengeId(wallet: String, challengeId: Long): B3trUserChallenge?

    @Query("{ 'wallet': ?0 }") fun findAllByWallet(wallet: String): List<B3trUserChallenge>

    @Query("{ 'challengeId': ?0 }")
    fun findAllByChallengeId(challengeId: Long): List<B3trUserChallenge>
}
