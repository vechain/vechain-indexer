package org.vechain.indexer.b3tr.action

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Service
import org.vechain.indexer.b3tr.AppId
import org.vechain.indexer.b3tr.action.SortFieldUtils.assertSortFields
import org.vechain.indexer.b3tr.action.response.AppLeaderboardItem
import org.vechain.indexer.b3tr.action.response.UserAppLeaderboardItem
import org.vechain.indexer.b3tr.action.response.UserLeaderboardItem
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.utils.CursorPaginationUtils.buildCursorQuery
import org.vechain.indexer.utils.CursorPaginationUtils.calculateNextCursor

@Profile("b3tr", "b3tr-actions")
@Service
open class ActionLeaderboardService(private val mongoTemplate: MongoTemplate) {

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

        val criteria =
            Criteria.where(UserAllTimeActionSummary::entityType.name).`is`(EntityType.USER)
        val (pageSize, query) =
            buildCursorQuery(
                baseCriteria = criteria,
                size = size,
                direction = direction,
                sortByField = sortBy,
                cursor = cursor,
                cursorField = UserAllTimeActionSummary::entity.name,
            )

        val results = mongoTemplate.find(query, UserAllTimeActionSummary::class.java)
        val items = results.take(pageSize).map { UserLeaderboardItem.from(it) }
        val nextCursor =
            calculateNextCursor(
                results = results,
                pageSize = pageSize,
                sortByField = sortBy,
                cursorField = UserAllTimeActionSummary::entity.name,
            )

        return paginatedResponse(
            data = items,
            hasNext = results.size > pageSize,
            cursor = nextCursor,
        )
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

        val criteria =
            Criteria.where(UserDailyActionSummary::entityType.name)
                .`is`(EntityType.USER)
                .and(UserDailyActionSummary::date.name)
                .`is`(date)
        val (pageSize, query) =
            buildCursorQuery(
                baseCriteria = criteria,
                size = size,
                direction = direction,
                sortByField = sortBy,
                cursor = cursor,
                cursorField = UserDailyActionSummary::entity.name,
            )

        val results = mongoTemplate.find(query, UserDailyActionSummary::class.java)
        val items = results.take(pageSize).map { UserLeaderboardItem.from(it) }
        val nextCursor =
            calculateNextCursor(
                results = results,
                pageSize = pageSize,
                sortByField = sortBy,
                cursorField = UserDailyActionSummary::entity.name,
            )

        return paginatedResponse(
            data = items,
            hasNext = results.size > pageSize,
            cursor = nextCursor,
        )
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

        val criteria =
            Criteria.where(UserRoundActionSummary::entityType.name)
                .`is`(EntityType.USER)
                .and(UserRoundActionSummary::roundId.name)
                .`is`(roundId)
        val (pageSize, query) =
            buildCursorQuery(
                baseCriteria = criteria,
                size = size,
                direction = direction,
                sortByField = sortBy,
                cursor = cursor,
                cursorField = UserRoundActionSummary::entity.name,
            )

        val results = mongoTemplate.find(query, UserRoundActionSummary::class.java)
        val items = results.take(pageSize).map { UserLeaderboardItem.from(it) }
        val nextCursor =
            calculateNextCursor(
                results = results,
                pageSize = pageSize,
                sortByField = sortBy,
                cursorField = UserRoundActionSummary::entity.name,
            )

        return paginatedResponse(
            data = items,
            hasNext = results.size > pageSize,
            cursor = nextCursor,
        )
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

        val criteria =
            Criteria.where(UserAllTimeActionSummary::entityType.name).`is`(EntityType.APP)
        val (pageSize, query) =
            buildCursorQuery(
                baseCriteria = criteria,
                size = size,
                direction = direction,
                sortByField = sortBy,
                cursor = cursor,
                cursorField = UserAllTimeActionSummary::entity.name,
            )

        val results = mongoTemplate.find(query, UserAllTimeActionSummary::class.java)
        val items = results.take(pageSize).map { AppLeaderboardItem.from(it) }
        val nextCursor =
            calculateNextCursor(
                results = results,
                pageSize = pageSize,
                sortByField = sortBy,
                cursorField = UserAllTimeActionSummary::entity.name,
            )

        return paginatedResponse(
            data = items,
            hasNext = results.size > pageSize,
            cursor = nextCursor,
        )
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

        val criteria =
            Criteria.where(UserDailyActionSummary::entityType.name)
                .`is`(EntityType.APP)
                .and(UserDailyActionSummary::date.name)
                .`is`(date)
        val (pageSize, query) =
            buildCursorQuery(
                baseCriteria = criteria,
                size = size,
                direction = direction,
                sortByField = sortBy,
                cursor = cursor,
                cursorField = UserDailyActionSummary::entity.name,
            )

        val results = mongoTemplate.find(query, UserDailyActionSummary::class.java)
        val items = results.take(pageSize).map { AppLeaderboardItem.from(it) }
        val nextCursor =
            calculateNextCursor(
                results = results,
                pageSize = pageSize,
                sortByField = sortBy,
                cursorField = UserDailyActionSummary::entity.name,
            )

        return paginatedResponse(
            data = items,
            hasNext = results.size > pageSize,
            cursor = nextCursor,
        )
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

        val criteria =
            Criteria.where(UserRoundActionSummary::entityType.name)
                .`is`(EntityType.APP)
                .and(UserRoundActionSummary::roundId.name)
                .`is`(roundId)
        val (pageSize, query) =
            buildCursorQuery(
                baseCriteria = criteria,
                size = size,
                direction = direction,
                sortByField = sortBy,
                cursor = cursor,
                cursorField = UserRoundActionSummary::entity.name,
            )

        val results = mongoTemplate.find(query, UserRoundActionSummary::class.java)
        val items = results.take(pageSize).map { AppLeaderboardItem.from(it) }
        val nextCursor =
            calculateNextCursor(
                results = results,
                pageSize = pageSize,
                sortByField = sortBy,
                cursorField = UserRoundActionSummary::entity.name,
            )

        return paginatedResponse(
            data = items,
            hasNext = results.size > pageSize,
            cursor = nextCursor,
        )
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

        val criteria = Criteria.where(AppAllTimeActionSummary::appId.name).`is`(appId.value)
        val (pageSize, query) =
            buildCursorQuery(
                baseCriteria = criteria,
                size = size,
                direction = direction,
                sortByField = sortBy,
                cursor = cursor,
                cursorField = AppAllTimeActionSummary::user.name,
            )

        val results = mongoTemplate.find(query, AppAllTimeActionSummary::class.java)
        val items = results.take(pageSize).map { UserAppLeaderboardItem.from(it) }
        val nextCursor =
            calculateNextCursor(
                results = results,
                pageSize = pageSize,
                sortByField = sortBy,
                cursorField = AppAllTimeActionSummary::user.name,
            )

        return paginatedResponse(
            data = items,
            hasNext = results.size > pageSize,
            cursor = nextCursor,
        )
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

        val criteria =
            Criteria.where(AppDailyActionSummary::appId.name)
                .`is`(appId.value)
                .and(AppDailyActionSummary::date.name)
                .`is`(date)
        val (pageSize, query) =
            buildCursorQuery(
                baseCriteria = criteria,
                size = size,
                direction = direction,
                sortByField = sortBy,
                cursor = cursor,
                cursorField = AppDailyActionSummary::user.name,
            )

        val results = mongoTemplate.find(query, AppDailyActionSummary::class.java)
        val items = results.take(pageSize).map { UserAppLeaderboardItem.from(it) }
        val nextCursor =
            calculateNextCursor(
                results = results,
                pageSize = pageSize,
                sortByField = sortBy,
                cursorField = UserAppLeaderboardItem::user.name,
            )

        return paginatedResponse(
            data = items,
            hasNext = results.size > pageSize,
            cursor = nextCursor,
        )
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

        val criteria =
            Criteria.where(AppRoundActionSummary::appId.name)
                .`is`(appId.value)
                .and(AppRoundActionSummary::roundId.name)
                .`is`(roundId)
        val (pageSize, query) =
            buildCursorQuery(
                baseCriteria = criteria,
                size = size,
                direction = direction,
                sortByField = sortBy,
                cursor = cursor,
                cursorField = AppRoundActionSummary::user.name,
            )

        val results = mongoTemplate.find(query, AppRoundActionSummary::class.java)
        val items = results.take(pageSize).map { UserAppLeaderboardItem.from(it) }
        val nextCursor =
            calculateNextCursor(
                results = results,
                pageSize = pageSize,
                sortByField = sortBy,
                cursorField = AppRoundActionSummary::user.name,
            )

        return paginatedResponse(
            data = items,
            hasNext = results.size > pageSize,
            cursor = nextCursor,
        )
    }
}
