package org.vechain.indexer.b3tr.challenges.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.b3tr.challenges.B3trChallenge
import org.vechain.indexer.b3tr.challenges.ChallengePhase
import org.vechain.indexer.b3tr.challenges.ChallengeStatus
import org.vechain.indexer.b3tr.challenges.ChallengeVisibility

@Profile("b3tr", "b3tr-challenges")
@Repository
interface B3trChallengeRepository : BaseIndexedRepository<B3trChallenge, String> {
    @Query("{ 'visibility': ?0 }")
    fun findByVisibility(visibility: ChallengeVisibility, pageable: Pageable): Slice<B3trChallenge>

    @Query("{ 'visibility': ?0, 'phase': ?1, 'lifecycleStatus': { \$in: ?2 } }")
    fun findByVisibilityAndPhaseAndLifecycleStatusIn(
        visibility: ChallengeVisibility,
        phase: ChallengePhase,
        lifecycleStatuses: Collection<ChallengeStatus>,
        pageable: Pageable,
    ): Slice<B3trChallenge>
}
