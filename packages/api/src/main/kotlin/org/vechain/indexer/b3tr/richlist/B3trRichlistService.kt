package org.vechain.indexer.b3tr.richlist

import java.math.BigDecimal
import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort.Direction
import org.springframework.stereotype.Service
import org.vechain.indexer.b3tr.balance.B3trBalanceReadRepository
import org.vechain.indexer.b3tr.richlist.response.B3trRankResponse
import org.vechain.indexer.b3tr.richlist.response.B3trRichlistItem
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.utils.CursorPaginationUtils

@Profile("b3tr", "b3tr-balance")
@Service
open class B3trRichlistService(
    private val repository: B3trBalanceReadRepository,
    private val b3trRichlistCountService: B3trRichlistCountService,
) {

    fun getRichlist(
        size: Int?,
        direction: String?,
        cursor: String? = null,
        scope: RichlistScope = RichlistScope.ALL,
    ): PaginatedResponse<B3trRichlistItem> {
        val pageSize = size ?: DEFAULT_PAGE_SIZE
        val sortDir = if (direction?.uppercase() == "ASC") Direction.ASC else Direction.DESC
        val cursorInfo = CursorPaginationUtils.parseCursor(cursor)

        val results =
            repository.page(
                on = scope.column,
                limit = pageSize + 1,
                direction = sortDir,
                cursorBalance = cursorInfo?.sortValue?.toBigDecimalOrNull(),
                cursorAddress = cursorInfo?.cursorValue,
            )
        val page = results.take(pageSize)
        if (page.isEmpty()) {
            return paginatedResponse(data = emptyList(), hasNext = false, cursor = null)
        }

        val startRank =
            b3trRichlistCountService.countBalancesGreaterThan(
                scope,
                scope.balanceFor(page.first()),
            ) + 1
        val items = page.mapIndexed { index, row ->
            B3trRichlistItem(
                address = row.address,
                balance = scope.balanceFor(row).toBigIntegerExact(),
                rank = startRank + index,
            )
        }

        val nextCursor =
            if (results.size <= pageSize) null
            else
                CursorPaginationUtils.generateCursor(
                    scope.balanceFor(page.last()).toPlainString(),
                    page.last().address,
                )

        return paginatedResponse(
            data = items,
            hasNext = results.size > pageSize,
            cursor = nextCursor,
        )
    }

    fun getAddressRank(
        address: String,
        scope: RichlistScope = RichlistScope.ALL,
    ): B3trRankResponse {
        val row =
            repository.findByAddress(address)
                ?: throw ResourceNotFoundException(
                    "Address not found in B3TR/VOT3 holders: $address"
                )
        val balance = scope.balanceFor(row)
        val totalHolders = b3trRichlistCountService.getPositiveHolderCount(scope)
        if (balance <= BigDecimal.ZERO) {
            return B3trRankResponse(
                address = address,
                balance = BigInteger.ZERO,
                rank = totalHolders + 1,
                totalHolders = totalHolders,
                topPercentage = if (totalHolders > 0) 100.0 else 0.0,
            )
        }
        val rank = b3trRichlistCountService.countBalancesGreaterThan(scope, balance) + 1
        val topPercentage = if (totalHolders > 0) (rank.toDouble() / totalHolders) * 100 else 0.0
        return B3trRankResponse(
            address = address,
            balance = balance.toBigIntegerExact(),
            rank = rank,
            totalHolders = totalHolders,
            topPercentage = topPercentage,
        )
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
    }
}
