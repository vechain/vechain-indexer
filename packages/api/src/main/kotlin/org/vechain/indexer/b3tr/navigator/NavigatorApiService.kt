package org.vechain.indexer.b3tr.navigator

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerService
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.PaginationUtils.offsetSlice

/** Read-only service behind the navigator API; one endpoint is one query. */
@Profile("b3tr")
@Service
open class NavigatorApiService(private val repository: NavigatorReadRepository) : IndexerService {

    open fun findNavigators(
        statuses: List<NavigatorStatus>?,
        sort: NavigatorSort,
        pageable: Pageable,
    ): Slice<Navigator> =
        offsetSlice(pageable, sort.property) { offset, limit, direction ->
            repository.findNavigators(statuses, sort, direction, offset, limit)
        }

    open fun getNavigatorById(navigatorId: String): Navigator? =
        repository.findNavigator(HexUtils.normalise(navigatorId))

    open fun getOverview(): NavigatorOverview = repository.overview()

    open fun findDelegationEvents(
        navigator: String?,
        citizen: String?,
        pageable: Pageable,
    ): Slice<NavigatorDelegationEvent> =
        offsetSlice(pageable, NavigatorDelegationEvent::blockTimestamp.name) {
            offset,
            limit,
            direction ->
            repository.findDelegationEvents(
                navigator?.let(HexUtils::normalise),
                citizen?.let(HexUtils::normalise),
                offset,
                limit,
                direction,
            )
        }

    open fun findCitizens(navigator: String, pageable: Pageable): Slice<NavigatorCitizen> =
        offsetSlice(pageable, NavigatorCitizen::delegatedAt.name) { offset, limit, direction ->
            repository.findCitizens(HexUtils.normalise(navigator), offset, limit, direction)
        }

    open fun getFeeSummary(navigator: String?): NavigatorFeeSummary =
        repository.feeSummary(navigator?.let(HexUtils::normalise))

    open fun findFeeHistory(navigator: String, pageable: Pageable): Slice<NavigatorFee> =
        offsetSlice(pageable, NavigatorFee::roundId.name) { offset, limit, direction ->
            repository.findFees(HexUtils.normalise(navigator), offset, limit, direction)
        }

    override fun getLatestIndexedBlocks(): Map<String, Long> =
        mapOf("Navigator" to repository.latestBlockNumber())
}
