package org.vechain.indexer.b3tr.action

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort.Direction
import org.springframework.stereotype.Service
import org.vechain.indexer.b3tr.AppId
import org.vechain.indexer.b3tr.action.response.AppLeaderboardItem
import org.vechain.indexer.b3tr.action.response.UserAppLeaderboardItem
import org.vechain.indexer.b3tr.action.response.UserLeaderboardItem
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.utils.CursorPaginationUtils

@Profile("b3tr", "b3tr-actions")
@Service
open class ActionLeaderboardService(private val repository: ActionReadRepository) {

    /**
     * A round that has closed can never be awarded another action, so its leaderboard is final. The
     * newest round on record is the one still open — or, if the indexer is behind a boundary, one
     * that has only just closed — so only the rounds behind it are settled. Everything else keeps
     * moving with every rewarded action, so it earns a minute and no more.
     */
    fun leaderboardPolicy(period: ActionPeriod): CachePolicy {
        val roundId = period.roundId ?: return CachePolicy.MINUTE
        val latestRound = repository.latestRound()
        return if (latestRound != null && roundId < latestRound) CachePolicy.IMMUTABLE
        else CachePolicy.MINUTE
    }

    fun getUserLeaderboard(
        period: ActionPeriod,
        size: Int?,
        direction: String?,
        sortBy: String,
        cursor: String? = null,
    ): PaginatedResponse<UserLeaderboardItem> =
        entities(
            period,
            EntityType.USER,
            size,
            direction,
            sortBy,
            cursor,
            UserLeaderboardItem::from,
        )

    fun getAppLeaderboard(
        period: ActionPeriod,
        size: Int?,
        direction: String?,
        sortBy: String,
        cursor: String? = null,
    ): PaginatedResponse<AppLeaderboardItem> =
        entities(period, EntityType.APP, size, direction, sortBy, cursor, AppLeaderboardItem::from)

    fun getUserAppLeaderboard(
        appId: AppId,
        period: ActionPeriod,
        size: Int?,
        direction: String?,
        sortBy: String,
        cursor: String? = null,
    ): PaginatedResponse<UserAppLeaderboardItem> {
        val pageSize = size ?: DEFAULT_PAGE_SIZE
        val sort = sortField(sortBy)
        val position = CursorPaginationUtils.parseCursor(cursor)
        val rows =
            repository.appLeaderboard(
                period,
                appId.value,
                sort,
                direction(direction),
                pageSize + 1,
                position?.sortValue,
                position?.cursorValue,
            )
        return page(rows, pageSize, UserAppLeaderboardItem::from) {
            CursorPaginationUtils.generateCursor(sort.valueOf(it), it.user)
        }
    }

    private fun <T : Any> entities(
        period: ActionPeriod,
        entityType: EntityType,
        size: Int?,
        direction: String?,
        sortBy: String,
        cursor: String?,
        item: (EntityActionSummary) -> T,
    ): PaginatedResponse<T> {
        val pageSize = size ?: DEFAULT_PAGE_SIZE
        val sort = sortField(sortBy)
        val position = CursorPaginationUtils.parseCursor(cursor)
        val rows =
            repository.leaderboard(
                period,
                entityType,
                sort,
                direction(direction),
                pageSize + 1,
                position?.sortValue,
                position?.cursorValue,
            )
        return page(rows, pageSize, item) {
            CursorPaginationUtils.generateCursor(sort.valueOf(it), it.entity)
        }
    }

    /** The cursor points at the last row of the page, which the next query resumes after. */
    private fun <R, T : Any> page(
        rows: List<R>,
        pageSize: Int,
        item: (R) -> T,
        cursorOf: (R) -> String,
    ): PaginatedResponse<T> {
        val page = rows.take(pageSize)
        val hasNext = rows.size > pageSize
        return paginatedResponse(
            data = page.map(item),
            hasNext = hasNext,
            cursor = if (hasNext) cursorOf(page.last()) else null,
        )
    }

    private fun sortField(sortBy: String): ActionSortField =
        ActionSortField.fromParameter(sortBy)
            ?: throw BadRequestException(
                "Invalid sortBy value: $sortBy. Allowed values are: " +
                    ActionSortField.entries.joinToString { it.parameter }
            )

    private fun direction(direction: String?): Direction =
        if (direction?.uppercase() == "ASC") Direction.ASC else Direction.DESC

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
    }
}
