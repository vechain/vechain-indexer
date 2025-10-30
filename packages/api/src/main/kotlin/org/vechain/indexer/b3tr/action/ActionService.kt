package org.vechain.indexer.b3tr.action

import java.time.LocalDate
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
        // Normalize the entity
        val normalizedEntity = HexUtils.normalise(wallet.value)

        // Retrieve the overview from the repository
        val overview = userAllTimeRepo.findByEntity(normalizedEntity)

        var rankByActionsRewarded: Long? = null
        var rankByReward: Long? = null

        if (overview != null) {
            // Calculate the position by totalRewardAmount
            rankByReward =
                userAllTimeRepo.countByTotalRewardAmountGreaterThanAndEntityType(
                    overview.totalRewardAmount,
                    EntityType.USER,
                ) + 1

            // Calculate the position by actionsRewarded
            rankByActionsRewarded =
                userAllTimeRepo.countByActionsRewardedGreaterThanAndEntityType(
                    overview.actionsRewarded,
                    EntityType.USER,
                ) + 1
        }

        // Fetch uniqueInteractions if the required profiles are active
        val uniqueInteractions: List<String> =
            appAllTimeRepo.findAppIdsByUser(normalizedEntity).map { it.appId }

        // Return UserOverview with the calculated position fields
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
        // Normalize the entity
        val normalizedEntity = HexUtils.normalise(wallet.value)

        // Retrieve the overview from the repository
        val overview = userDailyRepo.findByEntityAndDate(normalizedEntity, date)
        var rankByActionsRewarded: Long? = null
        var rankByReward: Long? = null

        if (overview != null) {
            // Calculate the position by totalRewardAmount
            rankByReward =
                userDailyRepo.countByTotalRewardAmountGreaterThanAndEntityTypeAndDate(
                    overview.totalRewardAmount,
                    EntityType.USER,
                    date,
                ) + 1

            // Calculate the position by actionsRewarded
            rankByActionsRewarded =
                userDailyRepo.countByActionsRewardedGreaterThanAndEntityTypeAndDate(
                    overview.actionsRewarded,
                    EntityType.USER,
                    date,
                ) + 1
        }

        // Fetch uniqueInteractions if the required profiles are active
        val uniqueInteractions: List<String> =
            appDailyRepo.findByUserAndDate(normalizedEntity, date).map { it.appId }

        // Return UserOverview with the calculated position fields
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
        // Normalize the entity
        val normalizedEntity = HexUtils.normalise(wallet.value)

        // Retrieve the overview from the repository
        val overview = userRoundRepo.findByEntityAndRoundId(normalizedEntity, roundId)

        var rankByActionsRewarded: Long? = null
        var rankByReward: Long? = null

        if (overview != null) {
            // Calculate the position by totalRewardAmount
            rankByReward =
                userRoundRepo.countByTotalRewardAmountGreaterThanAndEntityTypeAndRoundId(
                    overview.totalRewardAmount,
                    EntityType.USER,
                    roundId,
                ) + 1

            // Calculate the position by actionsRewarded
            rankByActionsRewarded =
                userRoundRepo.countByActionsRewardedGreaterThanAndEntityTypeAndRoundId(
                    overview.actionsRewarded,
                    EntityType.USER,
                    roundId,
                ) + 1
        }

        // Fetch uniqueInteractions if the required profiles are active
        val uniqueInteractions: List<String> =
            appRoundRepo.findAppIdsByUserAndRoundId(normalizedEntity, roundId).map { it.appId }

        // Return UserOverview with the calculated position fields
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

        var rankByActionsRewarded: Long? = null
        var rankByReward: Long? = null

        if (overview != null) {
            // Calculate the position by totalRewardAmount
            rankByReward =
                appAllTimeRepo.countByTotalRewardAmountGreaterThanAndAppId(
                    overview.totalRewardAmount,
                    appId.value,
                ) + 1

            // Calculate the position by actionsRewarded
            rankByActionsRewarded =
                appAllTimeRepo.countByActionsRewardedGreaterThanAndAppId(
                    overview.actionsRewarded,
                    appId.value,
                ) + 1
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

        var rankByActionsRewarded: Long? = null
        var rankByReward: Long? = null

        if (overview != null) {
            // Calculate the position by totalRewardAmount
            rankByReward =
                appDailyRepo.countByTotalRewardAmountGreaterThanAndAppIdAndDate(
                    overview.totalRewardAmount,
                    appId.value,
                    date,
                ) + 1

            // Calculate the position by actionsRewarded
            rankByActionsRewarded =
                appDailyRepo.countByActionsRewardedGreaterThanAndAppIdAndDate(
                    overview.actionsRewarded,
                    appId.value,
                    date,
                ) + 1
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

        var rankByActionsRewarded: Long? = null
        var rankByReward: Long? = null

        if (overview != null) {
            // Calculate the position by totalRewardAmount
            rankByReward =
                appRoundRepo.countByTotalRewardAmountGreaterThanAndAppIdAndRoundId(
                    overview.totalRewardAmount,
                    appId.value,
                    roundId,
                ) + 1

            // Calculate the position by actionsRewarded
            rankByActionsRewarded =
                appRoundRepo.countByActionsRewardedGreaterThanAndAppIdAndRoundId(
                    overview.actionsRewarded,
                    appId.value,
                    roundId,
                ) + 1
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

        // Retrieve the overview from the repository
        val overview = userAllTimeRepo.findByEntity(appId.value)

        var rankByActionsRewarded: Long? = null
        var rankByReward: Long? = null

        if (overview != null) {
            // Calculate the position by totalRewardAmount
            rankByReward =
                userAllTimeRepo.countByTotalRewardAmountGreaterThanAndEntityType(
                    overview.totalRewardAmount,
                    EntityType.APP,
                ) + 1

            // Calculate the position by actionsRewarded
            rankByActionsRewarded =
                userAllTimeRepo.countByActionsRewardedGreaterThanAndEntityType(
                    overview.actionsRewarded,
                    EntityType.APP,
                ) + 1
        }

        // Fetch totalUniqueUserInteractions
        val totalUniqueUserInteractions: Long = appAllTimeRepo.countByAppId(appId.value)

        // Return AppOverview with the calculated position fields
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
        // Retrieve the overview from the repository
        val overview = userRoundRepo.findByEntityAndRoundId(appId.value, roundId)

        var rankByActionsRewarded: Long? = null
        var rankByReward: Long? = null

        if (overview != null) {
            // Calculate the position by totalRewardAmount
            rankByReward =
                userRoundRepo.countByTotalRewardAmountGreaterThanAndEntityTypeAndRoundId(
                    overview.totalRewardAmount,
                    EntityType.APP,
                    roundId,
                ) + 1

            // Calculate the position by actionsRewarded
            rankByActionsRewarded =
                userRoundRepo.countByActionsRewardedGreaterThanAndEntityTypeAndRoundId(
                    overview.actionsRewarded,
                    EntityType.APP,
                    roundId,
                ) + 1
        }

        // Fetch totalUniqueUserInteractions
        val totalUniqueUserInteractions: Long =
            appRoundRepo.countByAppIdAndRoundId(appId.value, roundId)

        // Return AppOverview with the calculated position fields
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
        // Retrieve the overview from the repository
        val overview = userDailyRepo.findByEntityAndDate(appId.value, date)

        var rankByActionsRewarded: Long? = null
        var rankByReward: Long? = null

        if (overview != null) {
            // Calculate the position by totalRewardAmount
            rankByReward =
                userDailyRepo.countByTotalRewardAmountGreaterThanAndEntityTypeAndDate(
                    overview.totalRewardAmount,
                    EntityType.APP,
                    date,
                ) + 1

            // Calculate the position by actionsRewarded
            rankByActionsRewarded =
                userDailyRepo.countByActionsRewardedGreaterThanAndEntityTypeAndDate(
                    overview.actionsRewarded,
                    EntityType.APP,
                    date,
                ) + 1
        }

        // Fetch totalUniqueUserInteractions
        val totalUniqueUserInteractions: Long = appDailyRepo.countByAppIdAndDate(appId.value, date)

        // Return AppOverview with the calculated position fields
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
}
