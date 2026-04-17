package org.vechain.indexer.b3tr.navigator

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("b3tr", "b3tr-navigator")
@Repository
interface NavigatorRepository : BaseIndexedRepository<Navigator, String> {

    @Query("{ 'status': { '\$in': ?0 } }")
    fun findByStatusIn(statuses: List<NavigatorStatus>, pageable: Pageable): Slice<Navigator>

    fun findByStatus(status: NavigatorStatus): List<Navigator>

    fun findByStatusAndExitEffectiveDeadlineBlockLessThanEqual(
        status: NavigatorStatus,
        exitEffectiveDeadlineBlock: Long,
    ): List<Navigator>
}
