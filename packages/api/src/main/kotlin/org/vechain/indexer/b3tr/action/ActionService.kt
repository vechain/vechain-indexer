package org.vechain.indexer.b3tr.action

import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.b3tr.AppId
import org.vechain.indexer.b3tr.action.response.AppOverview
import org.vechain.indexer.b3tr.action.response.GlobalOverview
import org.vechain.indexer.b3tr.action.response.UserAppOverview
import org.vechain.indexer.b3tr.action.response.UserDailyActionSummary
import org.vechain.indexer.b3tr.action.response.UserOverview
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.history.HistoryReadRepository
import org.vechain.indexer.history.IndexedHistoryEvent
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.PaginationUtils.offsetSlice
import org.vechain.indexer.utils.PaginationUtils.toPageable

@Profile("b3tr", "b3tr-actions")
@Service
open class ActionService(
    private val historyRepo: HistoryReadRepository,
    private val repository: ActionReadRepository,
    @Qualifier("queryDispatcher") private val queryDispatcher: CoroutineDispatcher,
) {
    private data class Ranks(val byReward: Long, val byActions: Long)

    // User Actions
    open fun getUserActionsForApp(
        wallet: Address,
        appId: AppId,
        after: Long?,
        before: Long?,
        page: Int?,
        size: Int?,
        direction: String?,
    ): PaginatedResponse<Action> =
        actions(HexUtils.normalise(wallet.value), appId.value, after, before, page, size, direction)

    open fun getUserActions(
        wallet: Address,
        after: Long?,
        before: Long?,
        page: Int?,
        size: Int?,
        direction: String?,
    ): PaginatedResponse<Action> =
        actions(HexUtils.normalise(wallet.value), null, after, before, page, size, direction)

    fun getAppActions(
        appId: AppId,
        after: Long?,
        before: Long?,
        page: Int?,
        size: Int?,
        direction: String?,
    ): PaginatedResponse<Action> = actions(null, appId.value, after, before, page, size, direction)

    private fun actions(
        to: String?,
        appId: String?,
        after: Long?,
        before: Long?,
        page: Int?,
        size: Int?,
        direction: String?,
    ): PaginatedResponse<Action> {
        val pageable = toPageable(page, size, direction, IndexedHistoryEvent::blockTimestamp.name)
        val slice =
            offsetSlice(pageable, IndexedHistoryEvent::blockTimestamp.name) { offset, limit, dir ->
                historyRepo.findActions(to, appId, after, before, offset, limit, dir)
            }
        return paginatedResponse(slice.map { Action.from(it) })
    }

    // Overviews

    open fun getUserOverview(wallet: Address, period: ActionPeriod): UserOverview {
        val entity = HexUtils.normalise(wallet.value)
        val summary = repository.findEntity(period, EntityType.USER, entity)
        val (ranks, apps) =
            ranked(summary?.let { entityAhead(period, it) }) {
                repository.findAppIds(period, entity)
            }
        return UserOverview(
            wallet = entity,
            roundId = period.roundId,
            date = period.date,
            totalRewardAmount = summary?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = summary?.actionsRewarded ?: 0,
            totalImpact = summary?.totalImpact,
            rankByReward = ranks?.byReward,
            rankByActionsRewarded = ranks?.byActions,
            uniqueXAppInteractions = apps,
        )
    }

    open fun getUserAppOverview(
        wallet: Address,
        appId: AppId,
        period: ActionPeriod,
    ): UserAppOverview {
        val entity = HexUtils.normalise(wallet.value)
        val summary = repository.findAppUser(period, appId.value, entity)
        val (ranks, _) = ranked(summary?.let { appUserAhead(period, it) }) {}
        return UserAppOverview(
            wallet = entity,
            appId = appId.value,
            roundId = period.roundId,
            totalRewardAmount = summary?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = summary?.actionsRewarded ?: 0,
            totalImpact = summary?.totalImpact,
            rankByReward = ranks?.byReward,
            rankByActionsRewarded = ranks?.byActions,
        )
    }

    open fun getDailySummariesForRange(
        wallet: Address,
        startDate: String,
        endDate: String,
        page: Int?,
        size: Int?,
        direction: String?,
    ): PaginatedResponse<UserDailyActionSummary> {
        val entity = HexUtils.normalise(wallet.value)
        val pageable = toPageable(page, size, direction, DATE)
        if (LocalDate.parse(endDate).isBefore(LocalDate.parse(startDate))) {
            throw BadRequestException("End date must be equal or after start date")
        }
        return paginatedResponse(
            offsetSlice(pageable, DATE) { offset, limit, dir ->
                    repository.findDailyRange(entity, startDate, endDate, offset, limit, dir)
                }
                .map(UserDailyActionSummary::from)
        )
    }

    fun getAppOverview(appId: AppId, period: ActionPeriod): AppOverview {
        val summary = repository.findEntity(period, EntityType.APP, appId.value)
        val (ranks, _) = ranked(summary?.let { entityAhead(period, it) }) {}
        return AppOverview(
            appId = appId.value,
            roundId = period.roundId,
            date = period.date,
            totalRewardAmount = summary?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = summary?.actionsRewarded ?: 0,
            totalImpact = summary?.totalImpact,
            rankByReward = ranks?.byReward,
            rankByActionsRewarded = ranks?.byActions,
            totalUniqueUserInteractions = summary?.uniqueUsers ?: 0,
        )
    }

    fun getGlobalOverview(period: ActionPeriod): GlobalOverview {
        val summary = repository.findEntity(period, EntityType.GLOBAL, EntityType.GLOBAL.name)
        return GlobalOverview(
            roundId = period.roundId,
            date = period.date,
            totalRewardAmount = summary?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = summary?.actionsRewarded ?: 0,
            totalImpact = summary?.totalImpact,
            totalUniqueUserInteractions = summary?.uniqueUsers ?: 0,
        )
    }

    private fun entityAhead(
        period: ActionPeriod,
        s: EntityActionSummary,
    ): Pair<() -> Long, () -> Long> =
        Pair(
            {
                repository.countEntitiesAbove(
                    period,
                    s.entityType,
                    ActionSortField.TOTAL_REWARD_AMOUNT,
                    s.totalRewardAmount,
                )
            },
            {
                repository.countEntitiesAbove(
                    period,
                    s.entityType,
                    ActionSortField.ACTIONS_REWARDED,
                    s.actionsRewarded.toBigDecimal(),
                )
            },
        )

    private fun appUserAhead(
        period: ActionPeriod,
        s: AppUserActionSummary,
    ): Pair<() -> Long, () -> Long> =
        Pair(
            {
                repository.countAppUsersAbove(
                    period,
                    s.appId,
                    ActionSortField.TOTAL_REWARD_AMOUNT,
                    s.totalRewardAmount,
                )
            },
            {
                repository.countAppUsersAbove(
                    period,
                    s.appId,
                    ActionSortField.ACTIONS_REWARDED,
                    s.actionsRewarded.toBigDecimal(),
                )
            },
        )

    /** A rank is one more than the count ahead; both counts and [also] run together. */
    private fun <T> ranked(ahead: Pair<() -> Long, () -> Long>?, also: () -> T): Pair<Ranks?, T> =
        runBlocking {
            val reward = ahead?.let { async(queryDispatcher) { it.first() } }
            val actions = ahead?.let { async(queryDispatcher) { it.second() } }
            val extra = async(queryDispatcher) { also() }
            val ranks =
                if (reward != null && actions != null)
                    Ranks(reward.await() + 1, actions.await() + 1)
                else null
            ranks to extra.await()
        }

    companion object {
        private const val DATE = "date"
    }
}
