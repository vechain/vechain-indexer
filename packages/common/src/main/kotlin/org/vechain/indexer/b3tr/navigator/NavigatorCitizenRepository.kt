package org.vechain.indexer.b3tr.navigator

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("b3tr", "b3tr-navigator", "b3tr-navigator-citizen")
@Repository
interface NavigatorCitizenRepository : BaseIndexedRepository<NavigatorCitizen, String> {
    fun findByNavigatorAndActive(navigator: String, active: Boolean): List<NavigatorCitizen>

    fun findByActiveAndNavigatorExitEffectiveDeadlineBlockLessThanEqual(
        active: Boolean,
        navigatorExitEffectiveDeadlineBlock: Long,
    ): List<NavigatorCitizen>
}
