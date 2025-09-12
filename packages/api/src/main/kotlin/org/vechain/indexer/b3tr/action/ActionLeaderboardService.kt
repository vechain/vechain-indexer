package org.vechain.indexer.b3tr.action

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.b3tr.AppId
import org.vechain.indexer.b3tr.action.SortFieldUtils.assertSortFields
import org.vechain.indexer.b3tr.action.repository.AppAllTimeActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.AppDailyActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.AppRoundActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserAllTimeActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserDailyActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserRoundActionSummaryRepository
import org.vechain.indexer.b3tr.action.response.AppLeaderboardItem
import org.vechain.indexer.b3tr.action.response.UserAppLeaderboardItem
import org.vechain.indexer.b3tr.action.response.UserLeaderboardItem
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.utils.PaginationUtils.toPageable

@Profile("b3tr", "b3tr-actions")
@Service
open class ActionLeaderboardService(
    private val userAllTimeRepo: UserAllTimeActionSummaryRepository,
    private val userDailyRepo: UserDailyActionSummaryRepository,
    private val userRoundRepo: UserRoundActionSummaryRepository,
    private val appAllTimeRepo: AppAllTimeActionSummaryRepository,
    private val appDailyRepo: AppDailyActionSummaryRepository,
    private val appRoundRepo: AppRoundActionSummaryRepository,
) {

    // User leaderboards

    fun getUserAllTimeLeaderboard(
        page: Int?,
        size: Int?,
        direction: String?,
        sortBy: String,
    ): PaginatedResponse<UserLeaderboardItem> {
        // Ensure the sortBy field is valid
        assertSortFields(
            sortBy,
            UserAllTimeActionSummary::totalRewardAmount.name,
            UserAllTimeActionSummary::actionsRewarded.name,
        )

        val pageable = toPageable(page, size, direction, sortBy)

        val result = userAllTimeRepo.findAllByEntityType(EntityType.USER, pageable)

        return paginatedResponse(result.map { UserLeaderboardItem.from(it) })
    }

    fun getUserDailyLeaderboard(
        date: String,
        page: Int?,
        size: Int?,
        direction: String?,
        sortBy: String,
    ): PaginatedResponse<UserLeaderboardItem> {
        // Ensure the sortBy field is valid
        assertSortFields(
            sortBy,
            UserDailyActionSummary::totalRewardAmount.name,
            UserDailyActionSummary::actionsRewarded.name,
        )

        val pageable = toPageable(page, size, direction, sortBy)

        val result = userDailyRepo.findAllByEntityTypeAndDate(EntityType.USER, date, pageable)

        return paginatedResponse(result.map { UserLeaderboardItem.from(it) })
    }

    fun getUserRoundLeaderboard(
        roundId: Int,
        page: Int?,
        size: Int?,
        direction: String?,
        sortBy: String,
    ): PaginatedResponse<UserLeaderboardItem> {
        // Ensure the sortBy field is valid
        assertSortFields(
            sortBy,
            UserRoundActionSummary::totalRewardAmount.name,
            UserRoundActionSummary::actionsRewarded.name,
        )

        val pageable = toPageable(page, size, direction, sortBy)

        val result = userRoundRepo.findAllByEntityTypeAndRoundId(EntityType.USER, roundId, pageable)

        return paginatedResponse(result.map { UserLeaderboardItem.from(it) })
    }

    // App leaderboards

    fun getAppAllTimeLeaderboard(
        page: Int?,
        size: Int?,
        direction: String?,
        sortBy: String,
    ): PaginatedResponse<AppLeaderboardItem> {
        // Ensure the sortBy field is valid
        assertSortFields(
            sortBy,
            UserAllTimeActionSummary::totalRewardAmount.name,
            UserAllTimeActionSummary::actionsRewarded.name,
        )

        val pageable = toPageable(page, size, direction, sortBy)

        val result = userAllTimeRepo.findAllByEntityType(EntityType.APP, pageable)

        return paginatedResponse(result.map { AppLeaderboardItem.from(it) })
    }

    fun getAppDailyLeaderboard(
        date: String,
        page: Int?,
        size: Int?,
        direction: String?,
        sortBy: String,
    ): PaginatedResponse<AppLeaderboardItem> {
        // Ensure the sortBy field is valid
        assertSortFields(
            sortBy,
            UserDailyActionSummary::totalRewardAmount.name,
            UserDailyActionSummary::actionsRewarded.name,
        )

        val pageable = toPageable(page, size, direction, sortBy)

        val result = userDailyRepo.findAllByEntityTypeAndDate(EntityType.APP, date, pageable)

        return paginatedResponse(result.map { AppLeaderboardItem.from(it) })
    }

    fun getAppRoundLeaderboard(
        roundId: Int,
        page: Int?,
        size: Int?,
        direction: String?,
        sortBy: String,
    ): PaginatedResponse<AppLeaderboardItem> {
        // Ensure the sortBy field is valid
        assertSortFields(
            sortBy,
            UserRoundActionSummary::totalRewardAmount.name,
            UserRoundActionSummary::actionsRewarded.name,
        )

        val pageable = toPageable(page, size, direction, sortBy)

        val result = userRoundRepo.findAllByEntityTypeAndRoundId(EntityType.APP, roundId, pageable)

        return paginatedResponse(result.map { AppLeaderboardItem.from(it) })
    }

    // User leaderboards by app

    fun getUserAppAllTimeLeaderboard(
        appId: AppId,
        page: Int?,
        size: Int?,
        direction: String?,
        sortBy: String,
    ): PaginatedResponse<UserAppLeaderboardItem> {
        // Ensure the sortBy field is valid
        assertSortFields(
            sortBy,
            AppAllTimeActionSummary::totalRewardAmount.name,
            AppAllTimeActionSummary::actionsRewarded.name,
        )

        val pageable = toPageable(page, size, direction, sortBy)

        val result = appAllTimeRepo.findAllByAppId(appId.value, pageable)

        return paginatedResponse(result.map { UserAppLeaderboardItem.from(it) })
    }

    fun getUserAppDailyLeaderboard(
        appId: AppId,
        date: String,
        page: Int?,
        size: Int?,
        direction: String?,
        sortBy: String,
    ): PaginatedResponse<UserAppLeaderboardItem> {
        // Ensure the sortBy field is valid
        assertSortFields(
            sortBy,
            AppDailyActionSummary::totalRewardAmount.name,
            AppDailyActionSummary::actionsRewarded.name,
        )

        val pageable = toPageable(page, size, direction, sortBy)

        val result = appDailyRepo.findAllByAppIdAndDate(appId.value, date, pageable)

        return paginatedResponse(result.map { UserAppLeaderboardItem.from(it) })
    }

    fun getUserAppRoundLeaderboard(
        appId: AppId,
        roundId: Int,
        page: Int?,
        size: Int?,
        direction: String?,
        sortBy: String,
    ): PaginatedResponse<UserAppLeaderboardItem> {
        // Ensure the sortBy field is valid
        assertSortFields(
            sortBy,
            AppRoundActionSummary::totalRewardAmount.name,
            AppRoundActionSummary::actionsRewarded.name,
        )

        val pageable = toPageable(page, size, direction, sortBy)

        val result = appRoundRepo.findAllByAppIdAndRoundId(appId.value, roundId, pageable)

        return paginatedResponse(result.map { UserAppLeaderboardItem.from(it) })
    }
}
