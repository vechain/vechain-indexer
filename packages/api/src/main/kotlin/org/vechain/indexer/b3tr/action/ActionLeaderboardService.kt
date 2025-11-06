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

        var query: Query = Query(criteria)

        // Apply cursor condition if provided
        // Cursor format: "sortValue|cursorFieldValue"
        if (cursor != null && cursor.contains("|")) {
            val parts = cursor.split("|", limit = 2)
            val sortValue = parts[0]
            val cursorValue = parts[1]

            // Parse sort value based on the sort field type
            val parsedSortValue: Any =
                when {
                    sortBy.contains("actionsRewarded") -> sortValue.toLongOrNull() ?: sortValue
                    sortBy.contains("totalRewardAmount") ->
                        sortValue.toBigDecimalOrNull() ?: sortValue
                    else -> sortValue
                }

            // Keyset pagination: fetch records starting from the cursor position
            // The cursor points to the first record of the next page
            // We need: (sortField < value) OR (sortField == value AND cursorField <= cursorValue)
            // This handles ties in the sort field correctly
            if (sortDir == Sort.Direction.DESC) {
                // For DESC: (sortField < value) OR (sortField == value AND cursorField <=
                // cursorValue)
                val cond1 = Criteria.where(sortBy).lt(parsedSortValue)
                val cond2 =
                    Criteria.where(sortBy).`is`(parsedSortValue).and(cursorField).lte(cursorValue)
                // Create OR condition
                val orCriteria = Criteria().orOperator(cond1, cond2)
                query.addCriteria(orCriteria)
            } else {
                // For ASC: (sortField > value) OR (sortField == value AND cursorField >=
                // cursorValue)
                val cond1 = Criteria.where(sortBy).gt(parsedSortValue)
                val cond2 =
                    Criteria.where(sortBy).`is`(parsedSortValue).and(cursorField).gte(cursorValue)
                // Create OR condition
                val orCriteria = Criteria().orOperator(cond1, cond2)
                query.addCriteria(orCriteria)
            }
        }

        // Apply sort and limit - do this AFTER adding cursor criteria
        query.with(Sort.by(sortDir, sortBy).and(Sort.by(Sort.Direction.ASC, cursorField)))
        query.limit(pageSize + 1)

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
        val nextCursor =
            if (hasNext && results.isNotEmpty()) {
                val nextItem = results[pageSize]
                val sortValue =
                    when (sortBy) {
                        UserAllTimeActionSummary::totalRewardAmount.name ->
                            nextItem.totalRewardAmount.toString()
                        UserAllTimeActionSummary::actionsRewarded.name ->
                            nextItem.actionsRewarded.toString()
                        else -> nextItem.entity
                    }
                "$sortValue|${nextItem.entity}"
            } else null

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
        val nextCursor =
            if (hasNext && results.isNotEmpty()) {
                val nextItem = results[pageSize]
                val sortValue =
                    when (sortBy) {
                        UserDailyActionSummary::totalRewardAmount.name ->
                            nextItem.totalRewardAmount.toString()
                        UserDailyActionSummary::actionsRewarded.name ->
                            nextItem.actionsRewarded.toString()
                        else -> nextItem.entity
                    }
                "$sortValue|${nextItem.entity}"
            } else null

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
        val nextCursor =
            if (hasNext && results.isNotEmpty()) {
                val nextItem = results[pageSize]
                val sortValue =
                    when (sortBy) {
                        UserRoundActionSummary::totalRewardAmount.name ->
                            nextItem.totalRewardAmount.toString()
                        UserRoundActionSummary::actionsRewarded.name ->
                            nextItem.actionsRewarded.toString()
                        else -> nextItem.entity
                    }
                "$sortValue|${nextItem.entity}"
            } else null

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
        val nextCursor =
            if (hasNext && results.isNotEmpty()) {
                val nextItem = results[pageSize]
                val sortValue =
                    when (sortBy) {
                        UserAllTimeActionSummary::totalRewardAmount.name ->
                            nextItem.totalRewardAmount.toString()
                        UserAllTimeActionSummary::actionsRewarded.name ->
                            nextItem.actionsRewarded.toString()
                        else -> nextItem.entity
                    }
                "$sortValue|${nextItem.entity}"
            } else null

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
        val nextCursor =
            if (hasNext && results.isNotEmpty()) {
                val nextItem = results[pageSize]
                val sortValue =
                    when (sortBy) {
                        UserDailyActionSummary::totalRewardAmount.name ->
                            nextItem.totalRewardAmount.toString()
                        UserDailyActionSummary::actionsRewarded.name ->
                            nextItem.actionsRewarded.toString()
                        else -> nextItem.entity
                    }
                "$sortValue|${nextItem.entity}"
            } else null

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
        val nextCursor =
            if (hasNext && results.isNotEmpty()) {
                val nextItem = results[pageSize]
                val sortValue =
                    when (sortBy) {
                        UserRoundActionSummary::totalRewardAmount.name ->
                            nextItem.totalRewardAmount.toString()
                        UserRoundActionSummary::actionsRewarded.name ->
                            nextItem.actionsRewarded.toString()
                        else -> nextItem.entity
                    }
                "$sortValue|${nextItem.entity}"
            } else null

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
        val nextCursor =
            if (hasNext && results.isNotEmpty()) {
                val nextItem = results[pageSize]
                val sortValue =
                    when (sortBy) {
                        AppAllTimeActionSummary::totalRewardAmount.name ->
                            nextItem.totalRewardAmount.toString()
                        AppAllTimeActionSummary::actionsRewarded.name ->
                            nextItem.actionsRewarded.toString()
                        else -> nextItem.user
                    }
                "$sortValue|${nextItem.user}"
            } else null

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
        val nextCursor =
            if (hasNext && results.isNotEmpty()) {
                val nextItem = results[pageSize]
                val sortValue =
                    when (sortBy) {
                        AppDailyActionSummary::totalRewardAmount.name ->
                            nextItem.totalRewardAmount.toString()
                        AppDailyActionSummary::actionsRewarded.name ->
                            nextItem.actionsRewarded.toString()
                        else -> nextItem.user
                    }
                "$sortValue|${nextItem.user}"
            } else null

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
        val nextCursor =
            if (hasNext && results.isNotEmpty()) {
                val nextItem = results[pageSize]
                val sortValue =
                    when (sortBy) {
                        AppRoundActionSummary::totalRewardAmount.name ->
                            nextItem.totalRewardAmount.toString()
                        AppRoundActionSummary::actionsRewarded.name ->
                            nextItem.actionsRewarded.toString()
                        else -> nextItem.user
                    }
                "$sortValue|${nextItem.user}"
            } else null

        return paginatedResponse(items, hasNext, nextCursor)
    }
}
