package org.vechain.indexer.b3tr.action

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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
        size: Int?,
        direction: String?,
        sortBy: String,
        cursor: String? = null,
    ): PaginatedResponse<UserLeaderboardItem> {
        assertSortFields(
            sortBy,
            UserAllTimeActionSummary::totalRewardAmount.name,
            UserAllTimeActionSummary::actionsRewarded.name,
        )

        val pageSize = size ?: 20
        val pageable = PageRequest.of(0, pageSize, createSort(sortBy, direction))

        val results = userAllTimeRepo.findAllByEntityType(EntityType.USER, pageable)
        val items = results.content.map { UserLeaderboardItem.from(it) }

        return paginatedResponse(data = items, hasNext = results.hasNext(), cursor = null)
    }

    fun getUserDailyLeaderboard(
        date: String,
        size: Int?,
        direction: String?,
        sortBy: String,
        cursor: String? = null,
    ): PaginatedResponse<UserLeaderboardItem> {
        assertSortFields(
            sortBy,
            UserDailyActionSummary::totalRewardAmount.name,
            UserDailyActionSummary::actionsRewarded.name,
        )

        val pageSize = size ?: 20
        val pageable = PageRequest.of(0, pageSize, createSort(sortBy, direction))

        val results = userDailyRepo.findAllByEntityTypeAndDate(EntityType.USER, date, pageable)
        val items = results.content.map { UserLeaderboardItem.from(it) }

        return paginatedResponse(data = items, hasNext = results.hasNext(), cursor = null)
    }

    fun getUserRoundLeaderboard(
        roundId: Int,
        size: Int?,
        direction: String?,
        sortBy: String,
        cursor: String? = null,
    ): PaginatedResponse<UserLeaderboardItem> {
        assertSortFields(
            sortBy,
            UserRoundActionSummary::totalRewardAmount.name,
            UserRoundActionSummary::actionsRewarded.name,
        )

        val pageSize = size ?: 20
        val pageable = PageRequest.of(0, pageSize, createSort(sortBy, direction))

        val results =
            userRoundRepo.findAllByEntityTypeAndRoundId(EntityType.USER, roundId, pageable)
        val items = results.content.map { UserLeaderboardItem.from(it) }

        return paginatedResponse(data = items, hasNext = results.hasNext(), cursor = null)
    }

    // App leaderboards

    fun getAppAllTimeLeaderboard(
        size: Int?,
        direction: String?,
        sortBy: String,
        cursor: String? = null,
    ): PaginatedResponse<AppLeaderboardItem> {
        assertSortFields(
            sortBy,
            UserAllTimeActionSummary::totalRewardAmount.name,
            UserAllTimeActionSummary::actionsRewarded.name,
        )

        val pageSize = size ?: 20
        val pageable = PageRequest.of(0, pageSize, createSort(sortBy, direction))

        val results = userAllTimeRepo.findAllByEntityType(EntityType.APP, pageable)
        val items = results.content.map { AppLeaderboardItem.from(it) }

        return paginatedResponse(data = items, hasNext = results.hasNext(), cursor = null)
    }

    fun getAppDailyLeaderboard(
        date: String,
        size: Int?,
        direction: String?,
        sortBy: String,
        cursor: String? = null,
    ): PaginatedResponse<AppLeaderboardItem> {
        assertSortFields(
            sortBy,
            UserDailyActionSummary::totalRewardAmount.name,
            UserDailyActionSummary::actionsRewarded.name,
        )

        val pageSize = size ?: 20
        val pageable = PageRequest.of(0, pageSize, createSort(sortBy, direction))

        val results = userDailyRepo.findAllByEntityTypeAndDate(EntityType.APP, date, pageable)
        val items = results.content.map { AppLeaderboardItem.from(it) }

        return paginatedResponse(data = items, hasNext = results.hasNext(), cursor = null)
    }

    fun getAppRoundLeaderboard(
        roundId: Int,
        size: Int?,
        direction: String?,
        sortBy: String,
        cursor: String? = null,
    ): PaginatedResponse<AppLeaderboardItem> {
        assertSortFields(
            sortBy,
            UserRoundActionSummary::totalRewardAmount.name,
            UserRoundActionSummary::actionsRewarded.name,
        )

        val pageSize = size ?: 20
        val pageable = PageRequest.of(0, pageSize, createSort(sortBy, direction))

        val results = userRoundRepo.findAllByEntityTypeAndRoundId(EntityType.APP, roundId, pageable)
        val items = results.content.map { AppLeaderboardItem.from(it) }

        return paginatedResponse(data = items, hasNext = results.hasNext(), cursor = null)
    }

    // User leaderboards by app

    fun getUserAppAllTimeLeaderboard(
        appId: AppId,
        size: Int?,
        direction: String?,
        sortBy: String,
        cursor: String? = null,
    ): PaginatedResponse<UserAppLeaderboardItem> {
        assertSortFields(
            sortBy,
            AppAllTimeActionSummary::totalRewardAmount.name,
            AppAllTimeActionSummary::actionsRewarded.name,
        )

        val pageSize = size ?: 20
        val pageable = PageRequest.of(0, pageSize, createSort(sortBy, direction))

        val results = appAllTimeRepo.findAllByAppId(appId.value, pageable)
        val items = results.content.map { UserAppLeaderboardItem.from(it) }

        return paginatedResponse(data = items, hasNext = results.hasNext(), cursor = null)
    }

    fun getUserAppDailyLeaderboard(
        appId: AppId,
        date: String,
        size: Int?,
        direction: String?,
        sortBy: String,
        cursor: String? = null,
    ): PaginatedResponse<UserAppLeaderboardItem> {
        assertSortFields(
            sortBy,
            AppDailyActionSummary::totalRewardAmount.name,
            AppDailyActionSummary::actionsRewarded.name,
        )

        val pageSize = size ?: 20
        val pageable = PageRequest.of(0, pageSize, createSort(sortBy, direction))

        val results = appDailyRepo.findAllByAppIdAndDate(appId.value, date, pageable)
        val items = results.content.map { UserAppLeaderboardItem.from(it) }

        return paginatedResponse(data = items, hasNext = results.hasNext(), cursor = null)
    }

    fun getUserAppRoundLeaderboard(
        appId: AppId,
        roundId: Int,
        size: Int?,
        direction: String?,
        sortBy: String,
        cursor: String? = null,
    ): PaginatedResponse<UserAppLeaderboardItem> {
        assertSortFields(
            sortBy,
            AppRoundActionSummary::totalRewardAmount.name,
            AppRoundActionSummary::actionsRewarded.name,
        )

        val pageSize = size ?: 20
        val pageable = PageRequest.of(0, pageSize, createSort(sortBy, direction))

        val results = appRoundRepo.findAllByAppIdAndRoundId(appId.value, roundId, pageable)
        val items = results.content.map { UserAppLeaderboardItem.from(it) }

        return paginatedResponse(data = items, hasNext = results.hasNext(), cursor = null)
    }

    private fun createSort(sortBy: String, direction: String?): Sort {
        val sortDir =
            if (direction?.uppercase() == "ASC") Sort.Direction.ASC else Sort.Direction.DESC
        return Sort.by(sortDir, sortBy)
    }
}
