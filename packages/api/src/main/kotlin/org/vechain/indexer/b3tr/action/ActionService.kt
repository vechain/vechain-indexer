package org.vechain.indexer.b3tr.action

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.b3tr.AppId
import org.vechain.indexer.b3tr.action.repository.AppAllTimeActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.AppDailyActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.AppRoundActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserAllTimeActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserDailyActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserRoundActionSummaryRepository
import org.vechain.indexer.b3tr.action.response.AppOverview
import org.vechain.indexer.b3tr.action.response.GlobalOverview
import org.vechain.indexer.b3tr.action.response.UserAppOverview
import org.vechain.indexer.b3tr.action.response.UserOverview
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.history.HistoryEventName
import org.vechain.indexer.history.HistoryRepository
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.PaginationUtils.toPageable

@Profile("b3tr", "b3tr-actions")
@Service
open class ActionService(
    private val historyRepo: HistoryRepository,
    private val userAllTimeRepo: UserAllTimeActionSummaryRepository,
    private val userDailyRepo: UserDailyActionSummaryRepository,
    private val userRoundRepo: UserRoundActionSummaryRepository,
    private val appAllTimeRepo: AppAllTimeActionSummaryRepository,
    private val appDailyRepo: AppDailyActionSummaryRepository,
    private val appRoundRepo: AppRoundActionSummaryRepository,
) {
    // User Actions
    open fun getUserActionsForApp(
        wallet: Address,
        appId: AppId,
        after: Long?,
        before: Long?,
        page: Int?,
        size: Int?,
        direction: String?,
    ): PaginatedResponse<Action> {
        val pageable = toPageable(page, size, direction, "blockTimestamp")

        val result =
            if (after != null && before != null) {
                historyRepo.findAllByToAndAppIdAndEventNameAndBlockTimestampBetween(
                    HexUtils.normalise(wallet.value),
                    appId.value,
                    HistoryEventName.B3TR_ACTION.name,
                    after,
                    before,
                    pageable,
                )
            } else if (after != null) {
                historyRepo.findAllByToAndAppIdAndEventNameAndBlockTimestampAfter(
                    HexUtils.normalise(wallet.value),
                    appId.value,
                    HistoryEventName.B3TR_ACTION.name,
                    after,
                    pageable,
                )
            } else if (before != null) {
                historyRepo.findAllByToAndAppIdAndEventNameAndBlockTimestampBefore(
                    HexUtils.normalise(wallet.value),
                    appId.value,
                    HistoryEventName.B3TR_ACTION.name,
                    before,
                    pageable,
                )
            } else {
                historyRepo.findAllByToAndAppIdAndEventName(
                    HexUtils.normalise(wallet.value),
                    appId.value,
                    HistoryEventName.B3TR_ACTION.name,
                    pageable,
                )
            }

        return paginatedResponse(result.map { Action.from(it) })
    }

    open fun getUserActions(
        wallet: Address,
        after: Long?,
        before: Long?,
        page: Int?,
        size: Int?,
        direction: String?,
    ): PaginatedResponse<Action> {
        val pageable = toPageable(page, size, direction, "blockTimestamp")

        val result =
            if (after != null && before != null) {
                historyRepo.findAllByToAndEventNameAndBlockTimestampBetween(
                    HexUtils.normalise(wallet.value),
                    HistoryEventName.B3TR_ACTION.name,
                    after,
                    before,
                    pageable,
                )
            } else if (after != null) {
                historyRepo.findAllByToAndEventNameAndBlockTimestampAfter(
                    HexUtils.normalise(wallet.value),
                    HistoryEventName.B3TR_ACTION.name,
                    after,
                    pageable,
                )
            } else if (before != null) {
                historyRepo.findAllByToAndEventNameAndBlockTimestampBefore(
                    HexUtils.normalise(wallet.value),
                    HistoryEventName.B3TR_ACTION.name,
                    before,
                    pageable,
                )
            } else {
                historyRepo.findAllByToAndEventName(
                    HexUtils.normalise(wallet.value),
                    HistoryEventName.B3TR_ACTION.name,
                    pageable,
                )
            }

        return paginatedResponse(result.map { Action.from(it) })
    }

    fun getAppActions(
        appId: AppId,
        after: Long?,
        before: Long?,
        page: Int?,
        size: Int?,
        direction: String?,
    ): PaginatedResponse<Action> {
        val pageable = toPageable(page, size, direction, "blockTimestamp")

        val result =
            if (after != null && before != null) {
                historyRepo.findAllByAppIdAndEventNameAndBlockTimestampBetween(
                    appId.value,
                    HistoryEventName.B3TR_ACTION.name,
                    after,
                    before,
                    pageable,
                )
            } else if (after != null) {
                historyRepo.findAllByAppIdAndEventNameAndBlockTimestampAfter(
                    appId.value,
                    HistoryEventName.B3TR_ACTION.name,
                    after,
                    pageable,
                )
            } else if (before != null) {
                historyRepo.findAllByAppIdAndEventNameAndBlockTimestampBefore(
                    appId.value,
                    HistoryEventName.B3TR_ACTION.name,
                    before,
                    pageable,
                )
            } else {
                historyRepo.findAllByAppIdAndEventName(
                    appId.value,
                    HistoryEventName.B3TR_ACTION.name,
                    pageable,
                )
            }

        return paginatedResponse(result.map { Action.from(it) })
    }

    // User overviews

    open fun getAllTimeUserOverview(wallet: Address): UserOverview {
        val normalizedEntity = HexUtils.normalise(wallet.value)
        val overview = userAllTimeRepo.findByEntity(normalizedEntity)

        val rankByReward: Long?
        val rankByActionsRewarded: Long?
        val uniqueInteractions: List<String>

        if (overview != null) {
            val (reward, actions, interactions) =
                computeRanksAndQueryInParallel(
                    rankByRewardQuery = {
                        userAllTimeRepo.countByTotalRewardAmountGreaterThanAndEntityType(
                            overview.totalRewardAmount,
                            EntityType.USER,
                        )
                    },
                    rankByActionsQuery = {
                        userAllTimeRepo.countByActionsRewardedGreaterThanAndEntityType(
                            overview.actionsRewarded,
                            EntityType.USER,
                        )
                    },
                    additionalQuery = {
                        appAllTimeRepo.findAppIdsByUser(normalizedEntity).map { it.appId }
                    },
                )
            rankByReward = reward
            rankByActionsRewarded = actions
            uniqueInteractions = interactions
        } else {
            rankByReward = null
            rankByActionsRewarded = null
            uniqueInteractions = appAllTimeRepo.findAppIdsByUser(normalizedEntity).map { it.appId }
        }

        return UserOverview(
            wallet = normalizedEntity,
            totalRewardAmount = overview?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = overview?.actionsRewarded ?: 0,
            totalImpact = overview?.totalImpact,
            rankByReward = rankByReward,
            rankByActionsRewarded = rankByActionsRewarded,
            uniqueXAppInteractions = uniqueInteractions,
            roundId = null,
            date = null,
        )
    }

    open fun getDailyUserOverview(wallet: Address, date: String): UserOverview {
        val normalizedEntity = HexUtils.normalise(wallet.value)
        val overview = userDailyRepo.findByEntityAndDate(normalizedEntity, date)

        val rankByReward: Long?
        val rankByActionsRewarded: Long?
        val uniqueInteractions: List<String>

        if (overview != null) {
            val (reward, actions, interactions) =
                computeRanksAndQueryInParallel(
                    rankByRewardQuery = {
                        userDailyRepo.countByTotalRewardAmountGreaterThanAndEntityTypeAndDate(
                            overview.totalRewardAmount,
                            EntityType.USER,
                            date,
                        )
                    },
                    rankByActionsQuery = {
                        userDailyRepo.countByActionsRewardedGreaterThanAndEntityTypeAndDate(
                            overview.actionsRewarded,
                            EntityType.USER,
                            date,
                        )
                    },
                    additionalQuery = {
                        appDailyRepo.findByUserAndDate(normalizedEntity, date).map { it.appId }
                    },
                )
            rankByReward = reward
            rankByActionsRewarded = actions
            uniqueInteractions = interactions
        } else {
            rankByReward = null
            rankByActionsRewarded = null
            uniqueInteractions =
                appDailyRepo.findByUserAndDate(normalizedEntity, date).map { it.appId }
        }

        return UserOverview(
            wallet = normalizedEntity,
            date = date,
            totalRewardAmount = overview?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = overview?.actionsRewarded ?: 0,
            totalImpact = overview?.totalImpact,
            rankByReward = rankByReward,
            rankByActionsRewarded = rankByActionsRewarded,
            uniqueXAppInteractions = uniqueInteractions,
            roundId = null,
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
        val walletNormalised = HexUtils.normalise(wallet.value)
        val pageable = toPageable(page, size, direction, "date")
        val start = LocalDate.parse(startDate)
        val end = LocalDate.parse(endDate)
        if (end.isBefore(start)) {
            throw BadRequestException("End date must be equal or after start date")
        }
        return paginatedResponse(
            userDailyRepo.findAllByEntityAndDateBetween(
                walletNormalised,
                startDate,
                endDate,
                pageable,
            )
        )
    }

    open fun getRoundUserOverview(wallet: Address, roundId: Int): UserOverview {
        val normalizedEntity = HexUtils.normalise(wallet.value)
        val overview = userRoundRepo.findByEntityAndRoundId(normalizedEntity, roundId)

        val rankByReward: Long?
        val rankByActionsRewarded: Long?
        val uniqueInteractions: List<String>

        if (overview != null) {
            val (reward, actions, interactions) =
                computeRanksAndQueryInParallel(
                    rankByRewardQuery = {
                        userRoundRepo.countByTotalRewardAmountGreaterThanAndEntityTypeAndRoundId(
                            overview.totalRewardAmount,
                            EntityType.USER,
                            roundId,
                        )
                    },
                    rankByActionsQuery = {
                        userRoundRepo.countByActionsRewardedGreaterThanAndEntityTypeAndRoundId(
                            overview.actionsRewarded,
                            EntityType.USER,
                            roundId,
                        )
                    },
                    additionalQuery = {
                        appRoundRepo.findAppIdsByUserAndRoundId(normalizedEntity, roundId).map {
                            it.appId
                        }
                    },
                )
            rankByReward = reward
            rankByActionsRewarded = actions
            uniqueInteractions = interactions
        } else {
            rankByReward = null
            rankByActionsRewarded = null
            uniqueInteractions =
                appRoundRepo.findAppIdsByUserAndRoundId(normalizedEntity, roundId).map { it.appId }
        }

        return UserOverview(
            wallet = normalizedEntity,
            roundId = roundId,
            totalRewardAmount = overview?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = overview?.actionsRewarded ?: 0,
            totalImpact = overview?.totalImpact,
            rankByReward = rankByReward,
            rankByActionsRewarded = rankByActionsRewarded,
            uniqueXAppInteractions = uniqueInteractions,
            date = null,
        )
    }

    // User/App Overviews

    open fun getAllTimeUserAppOverview(wallet: Address, appId: AppId): UserAppOverview {
        val normalizedEntity = HexUtils.normalise(wallet.value)
        val overview = appAllTimeRepo.findByAppIdAndUser(appId.value, normalizedEntity)

        var rankByReward: Long? = null
        var rankByActionsRewarded: Long? = null

        if (overview != null) {
            val (reward, actions) =
                computeRanksInParallel(
                    rankByRewardQuery = {
                        appAllTimeRepo.countByTotalRewardAmountGreaterThanAndAppId(
                            overview.totalRewardAmount,
                            appId.value,
                        )
                    },
                    rankByActionsQuery = {
                        appAllTimeRepo.countByActionsRewardedGreaterThanAndAppId(
                            overview.actionsRewarded,
                            appId.value,
                        )
                    },
                )
            rankByReward = reward
            rankByActionsRewarded = actions
        }

        return UserAppOverview(
            wallet = normalizedEntity,
            appId = appId.value,
            totalRewardAmount = overview?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = overview?.actionsRewarded ?: 0,
            totalImpact = overview?.totalImpact,
            rankByReward = rankByReward,
            rankByActionsRewarded = rankByActionsRewarded,
            roundId = null,
        )
    }

    open fun getDailyUserAppOverview(wallet: Address, appId: AppId, date: String): UserAppOverview {
        val normalizedEntity = HexUtils.normalise(wallet.value)
        val overview = appDailyRepo.findByAppIdAndUserAndDate(appId.value, normalizedEntity, date)

        var rankByReward: Long? = null
        var rankByActionsRewarded: Long? = null

        if (overview != null) {
            val (reward, actions) =
                computeRanksInParallel(
                    rankByRewardQuery = {
                        appDailyRepo.countByTotalRewardAmountGreaterThanAndAppIdAndDate(
                            overview.totalRewardAmount,
                            appId.value,
                            date,
                        )
                    },
                    rankByActionsQuery = {
                        appDailyRepo.countByActionsRewardedGreaterThanAndAppIdAndDate(
                            overview.actionsRewarded,
                            appId.value,
                            date,
                        )
                    },
                )
            rankByReward = reward
            rankByActionsRewarded = actions
        }

        return UserAppOverview(
            wallet = normalizedEntity,
            appId = appId.value,
            totalRewardAmount = overview?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = overview?.actionsRewarded ?: 0,
            totalImpact = overview?.totalImpact,
            rankByReward = rankByReward,
            rankByActionsRewarded = rankByActionsRewarded,
            roundId = null,
        )
    }

    open fun getRoundUserAppOverview(wallet: Address, appId: AppId, roundId: Int): UserAppOverview {
        val normalizedEntity = HexUtils.normalise(wallet.value)
        val overview =
            appRoundRepo.findByAppIdAndUserAndRoundId(appId.value, normalizedEntity, roundId)

        var rankByReward: Long? = null
        var rankByActionsRewarded: Long? = null

        if (overview != null) {
            val (reward, actions) =
                computeRanksInParallel(
                    rankByRewardQuery = {
                        appRoundRepo.countByTotalRewardAmountGreaterThanAndAppIdAndRoundId(
                            overview.totalRewardAmount,
                            appId.value,
                            roundId,
                        )
                    },
                    rankByActionsQuery = {
                        appRoundRepo.countByActionsRewardedGreaterThanAndAppIdAndRoundId(
                            overview.actionsRewarded,
                            appId.value,
                            roundId,
                        )
                    },
                )
            rankByReward = reward
            rankByActionsRewarded = actions
        }

        return UserAppOverview(
            wallet = normalizedEntity,
            appId = appId.value,
            roundId = roundId,
            totalRewardAmount = overview?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = overview?.actionsRewarded ?: 0,
            totalImpact = overview?.totalImpact,
            rankByReward = rankByReward,
            rankByActionsRewarded = rankByActionsRewarded,
        )
    }

    // App Overviews

    fun getAppAllTimeOverview(appId: AppId): AppOverview {
        val overview = userAllTimeRepo.findByEntity(appId.value)

        val rankByReward: Long?
        val rankByActionsRewarded: Long?
        val totalUniqueUserInteractions: Long

        if (overview != null) {
            val (reward, actions, uniqueUsers) =
                computeRanksAndQueryInParallel(
                    rankByRewardQuery = {
                        userAllTimeRepo.countByTotalRewardAmountGreaterThanAndEntityType(
                            overview.totalRewardAmount,
                            EntityType.APP,
                        )
                    },
                    rankByActionsQuery = {
                        userAllTimeRepo.countByActionsRewardedGreaterThanAndEntityType(
                            overview.actionsRewarded,
                            EntityType.APP,
                        )
                    },
                    additionalQuery = { appAllTimeRepo.countByAppId(appId.value) },
                )
            rankByReward = reward
            rankByActionsRewarded = actions
            totalUniqueUserInteractions = uniqueUsers
        } else {
            rankByReward = null
            rankByActionsRewarded = null
            totalUniqueUserInteractions = appAllTimeRepo.countByAppId(appId.value)
        }

        return AppOverview(
            appId = appId.value,
            totalRewardAmount = overview?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = overview?.actionsRewarded ?: 0,
            totalImpact = overview?.totalImpact,
            rankByReward = rankByReward,
            rankByActionsRewarded = rankByActionsRewarded,
            totalUniqueUserInteractions = totalUniqueUserInteractions,
            roundId = null,
            date = null,
        )
    }

    fun getAppRoundOverview(appId: AppId, roundId: Int): AppOverview {
        val overview = userRoundRepo.findByEntityAndRoundId(appId.value, roundId)

        val rankByReward: Long?
        val rankByActionsRewarded: Long?
        val totalUniqueUserInteractions: Long

        if (overview != null) {
            val (reward, actions, uniqueUsers) =
                computeRanksAndQueryInParallel(
                    rankByRewardQuery = {
                        userRoundRepo.countByTotalRewardAmountGreaterThanAndEntityTypeAndRoundId(
                            overview.totalRewardAmount,
                            EntityType.APP,
                            roundId,
                        )
                    },
                    rankByActionsQuery = {
                        userRoundRepo.countByActionsRewardedGreaterThanAndEntityTypeAndRoundId(
                            overview.actionsRewarded,
                            EntityType.APP,
                            roundId,
                        )
                    },
                    additionalQuery = { appRoundRepo.countByAppIdAndRoundId(appId.value, roundId) },
                )
            rankByReward = reward
            rankByActionsRewarded = actions
            totalUniqueUserInteractions = uniqueUsers
        } else {
            rankByReward = null
            rankByActionsRewarded = null
            totalUniqueUserInteractions = appRoundRepo.countByAppIdAndRoundId(appId.value, roundId)
        }

        return AppOverview(
            appId = appId.value,
            roundId = roundId,
            totalRewardAmount = overview?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = overview?.actionsRewarded ?: 0,
            totalImpact = overview?.totalImpact,
            rankByReward = rankByReward,
            rankByActionsRewarded = rankByActionsRewarded,
            totalUniqueUserInteractions = totalUniqueUserInteractions,
            date = null,
        )
    }

    fun getAppDailyOverview(appId: AppId, date: String): AppOverview {
        val overview = userDailyRepo.findByEntityAndDate(appId.value, date)

        val rankByReward: Long?
        val rankByActionsRewarded: Long?
        val totalUniqueUserInteractions: Long

        if (overview != null) {
            val (reward, actions, uniqueUsers) =
                computeRanksAndQueryInParallel(
                    rankByRewardQuery = {
                        userDailyRepo.countByTotalRewardAmountGreaterThanAndEntityTypeAndDate(
                            overview.totalRewardAmount,
                            EntityType.APP,
                            date,
                        )
                    },
                    rankByActionsQuery = {
                        userDailyRepo.countByActionsRewardedGreaterThanAndEntityTypeAndDate(
                            overview.actionsRewarded,
                            EntityType.APP,
                            date,
                        )
                    },
                    additionalQuery = { appDailyRepo.countByAppIdAndDate(appId.value, date) },
                )
            rankByReward = reward
            rankByActionsRewarded = actions
            totalUniqueUserInteractions = uniqueUsers
        } else {
            rankByReward = null
            rankByActionsRewarded = null
            totalUniqueUserInteractions = appDailyRepo.countByAppIdAndDate(appId.value, date)
        }

        return AppOverview(
            appId = appId.value,
            date = date,
            totalRewardAmount = overview?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = overview?.actionsRewarded ?: 0,
            totalImpact = overview?.totalImpact,
            rankByReward = rankByReward,
            rankByActionsRewarded = rankByActionsRewarded,
            totalUniqueUserInteractions = totalUniqueUserInteractions,
            roundId = null,
        )
    }

    // Global Overviews
    fun getGlobalAllTimeOverview(): GlobalOverview {
        val overview = userAllTimeRepo.findByEntity(EntityType.GLOBAL.name)

        val distinctUsers = userAllTimeRepo.countByEntityType(EntityType.USER)

        return GlobalOverview(
            roundId = null,
            date = null,
            totalRewardAmount = overview?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = overview?.actionsRewarded ?: 0,
            totalImpact = overview?.totalImpact,
            totalUniqueUserInteractions = distinctUsers,
        )
    }

    fun getGlobalDailyOverview(date: String): GlobalOverview {
        val overview = userDailyRepo.findByEntityAndDate(EntityType.GLOBAL.name, date)

        val distinctUsers = userDailyRepo.countByEntityTypeAndDate(EntityType.USER, date)

        return GlobalOverview(
            roundId = null,
            date = date,
            totalRewardAmount = overview?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = overview?.actionsRewarded ?: 0,
            totalImpact = overview?.totalImpact,
            totalUniqueUserInteractions = distinctUsers,
        )
    }

    fun getGlobalRoundOverview(roundId: Int): GlobalOverview {
        val overview = userRoundRepo.findByEntityAndRoundId(EntityType.GLOBAL.name, roundId)

        val distinctUsers = userRoundRepo.countByEntityTypeAndRoundId(EntityType.USER, roundId)

        return GlobalOverview(
            roundId = roundId,
            date = null,
            totalRewardAmount = overview?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = overview?.actionsRewarded ?: 0,
            totalImpact = overview?.totalImpact,
            totalUniqueUserInteractions = distinctUsers,
        )
    }

    private fun computeRanksInParallel(
        rankByRewardQuery: () -> Long,
        rankByActionsQuery: () -> Long,
    ): Pair<Long, Long> =
        runBlocking(Dispatchers.IO) {
            val rewardDeferred = async { rankByRewardQuery() }
            val actionsDeferred = async { rankByActionsQuery() }
            Pair(rewardDeferred.await() + 1, actionsDeferred.await() + 1)
        }

    private fun <T> computeRanksAndQueryInParallel(
        rankByRewardQuery: () -> Long,
        rankByActionsQuery: () -> Long,
        additionalQuery: () -> T,
    ): Triple<Long, Long, T> =
        runBlocking(Dispatchers.IO) {
            val rewardDeferred = async { rankByRewardQuery() }
            val actionsDeferred = async { rankByActionsQuery() }
            val additionalDeferred = async { additionalQuery() }
            Triple(
                rewardDeferred.await() + 1,
                actionsDeferred.await() + 1,
                additionalDeferred.await(),
            )
        }
}
