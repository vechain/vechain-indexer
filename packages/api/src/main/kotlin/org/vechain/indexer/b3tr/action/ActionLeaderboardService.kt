package org.vechain.indexer.b3tr.action

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.vechain.indexer.b3tr.AppId
import org.vechain.indexer.b3tr.action.SortFieldUtils.assertSortFields
import org.vechain.indexer.b3tr.action.response.AppLeaderboardItem
import org.vechain.indexer.b3tr.action.response.UserAppLeaderboardItem
import org.vechain.indexer.b3tr.action.response.UserLeaderboardItem
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse

@Profile("b3tr", "b3tr-actions")
@Service
open class ActionLeaderboardService(private val mongoTemplate: MongoTemplate) {

    private fun buildCursorQuery(
        criteria: Criteria,
        size: Int?,
        direction: String?,
        sortBy: String,
        cursor: String?,
        cursorField: String = "entity",
    ): Pair<Int, Query> {
        val pageSize = size ?: 20
        val sortDir =
            if (direction?.uppercase() == "ASC") Sort.Direction.ASC else Sort.Direction.DESC

        if (cursor != null) {
            if (sortDir == Sort.Direction.ASC) {
                criteria.and(cursorField).gt(cursor)
            } else {
                criteria.and(cursorField).lt(cursor)
            }
        }

        val query =
            Query(criteria)
                .with(Sort.by(sortDir, sortBy).and(Sort.by(Sort.Direction.ASC, cursorField)))
                .limit(pageSize + 1)

        return pageSize to query
    }

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
        val (pageSize, query) = buildCursorQuery(criteria, size, direction, sortBy, cursor)

        val results = mongoTemplate.find(query, UserAllTimeActionSummary::class.java)
        val hasNext = results.size > pageSize
        val items = results.take(pageSize).map { UserLeaderboardItem.from(it) }
        val nextCursor = if (hasNext && results.isNotEmpty()) results[pageSize].entity else null

        return paginatedResponse(items, hasNext, nextCursor)
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
        val (pageSize, query) = buildCursorQuery(criteria, size, direction, sortBy, cursor)

        val results = mongoTemplate.find(query, UserDailyActionSummary::class.java)
        val hasNext = results.size > pageSize
        val items = results.take(pageSize).map { UserLeaderboardItem.from(it) }
        val nextCursor = if (hasNext && results.isNotEmpty()) results[pageSize].entity else null

        return paginatedResponse(items, hasNext, nextCursor)
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
        val (pageSize, query) = buildCursorQuery(criteria, size, direction, sortBy, cursor)

        val results = mongoTemplate.find(query, UserRoundActionSummary::class.java)
        val hasNext = results.size > pageSize
        val items = results.take(pageSize).map { UserLeaderboardItem.from(it) }
        val nextCursor = if (hasNext && results.isNotEmpty()) results[pageSize].entity else null

        return paginatedResponse(items, hasNext, nextCursor)
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
        val (pageSize, query) = buildCursorQuery(criteria, size, direction, sortBy, cursor)

        val results = mongoTemplate.find(query, UserAllTimeActionSummary::class.java)
        val hasNext = results.size > pageSize
        val items = results.take(pageSize).map { AppLeaderboardItem.from(it) }
        val nextCursor = if (hasNext && results.isNotEmpty()) results[pageSize].entity else null

        return paginatedResponse(items, hasNext, nextCursor)
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
        val (pageSize, query) = buildCursorQuery(criteria, size, direction, sortBy, cursor)

        val results = mongoTemplate.find(query, UserDailyActionSummary::class.java)
        val hasNext = results.size > pageSize
        val items = results.take(pageSize).map { AppLeaderboardItem.from(it) }
        val nextCursor = if (hasNext && results.isNotEmpty()) results[pageSize].entity else null

        return paginatedResponse(items, hasNext, nextCursor)
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
        val (pageSize, query) = buildCursorQuery(criteria, size, direction, sortBy, cursor)

        val results = mongoTemplate.find(query, UserRoundActionSummary::class.java)
        val hasNext = results.size > pageSize
        val items = results.take(pageSize).map { AppLeaderboardItem.from(it) }
        val nextCursor = if (hasNext && results.isNotEmpty()) results[pageSize].entity else null

        return paginatedResponse(items, hasNext, nextCursor)
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
                criteria,
                size,
                direction,
                sortBy,
                cursor,
                AppAllTimeActionSummary::user.name,
            )

        val results = mongoTemplate.find(query, AppAllTimeActionSummary::class.java)
        val hasNext = results.size > pageSize
        val items = results.take(pageSize).map { UserAppLeaderboardItem.from(it) }
        val nextCursor = if (hasNext && results.isNotEmpty()) results[pageSize].user else null

        return paginatedResponse(items, hasNext, nextCursor)
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
                criteria,
                size,
                direction,
                sortBy,
                cursor,
                AppDailyActionSummary::user.name,
            )

        val results = mongoTemplate.find(query, AppDailyActionSummary::class.java)
        val hasNext = results.size > pageSize
        val items = results.take(pageSize).map { UserAppLeaderboardItem.from(it) }
        val nextCursor = if (hasNext && results.isNotEmpty()) results[pageSize].user else null

        return paginatedResponse(items, hasNext, nextCursor)
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
                criteria,
                size,
                direction,
                sortBy,
                cursor,
                AppRoundActionSummary::user.name,
            )

        val results = mongoTemplate.find(query, AppRoundActionSummary::class.java)
        val hasNext = results.size > pageSize
        val items = results.take(pageSize).map { UserAppLeaderboardItem.from(it) }
        val nextCursor = if (hasNext && results.isNotEmpty()) results[pageSize].user else null

        return paginatedResponse(items, hasNext, nextCursor)
    }
}
