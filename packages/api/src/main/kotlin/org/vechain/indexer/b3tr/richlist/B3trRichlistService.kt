package org.vechain.indexer.b3tr.richlist

import java.math.BigDecimal
import java.math.BigInteger
import org.bson.types.Decimal128
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.b3tr.balance.B3trBalance
import org.vechain.indexer.b3tr.balance.repository.B3trBalanceRepository
import org.vechain.indexer.b3tr.richlist.response.B3trRankResponse
import org.vechain.indexer.b3tr.richlist.response.B3trRichlistItem
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.utils.CursorPaginationUtils

@Profile("b3tr", "b3tr-balance")
@Service
open class B3trRichlistService(
    private val mongoTemplate: MongoTemplate,
    private val b3trRepository: B3trBalanceRepository,
) {

    private val collection = IndexerNames.B3TR_BALANCE.COLLECTION
    private val decimalZero = Decimal128(BigDecimal.ZERO)

    fun getRichlist(
        size: Int?,
        direction: String?,
        cursor: String? = null,
        scope: RichlistScope = RichlistScope.ALL,
    ): PaginatedResponse<B3trRichlistItem> {
        val sortField = sortFieldForScope(scope)
        val criteria = Criteria.where(sortField).gt(decimalZero)
        val (pageSize, query) =
            CursorPaginationUtils.buildCursorQuery(
                baseCriteria = criteria,
                size = size,
                direction = direction,
                sortByField = sortField,
                cursor = cursor,
                cursorField = "_id",
            )

        val results = mongoTemplate.find(query, B3trBalance::class.java, collection)
        val page = results.take(pageSize)

        if (page.isEmpty()) {
            return paginatedResponse(data = emptyList(), hasNext = false, cursor = null)
        }

        val first = page.first()
        val firstBalance = balanceForScope(first, scope)
        val startRank =
            mongoTemplate.count(
                Query(Criteria.where(sortField).gt(firstBalance)),
                B3trBalance::class.java,
                collection,
            ) + 1

        val items =
            page.mapIndexed { index, doc ->
                B3trRichlistItem(
                    address = doc.address,
                    balance = balanceForScope(doc, scope).bigDecimalValue().toBigInteger(),
                    rank = startRank + index,
                )
            }

        val nextCursor =
            CursorPaginationUtils.calculateNextCursor(
                results = results,
                pageSize = pageSize,
                sortByField = sortField,
                cursorField = "address",
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
        val doc =
            b3trRepository.findById(address).orElse(null)
                ?: throw ResourceNotFoundException(
                    "Address not found in B3TR/VOT3 holders: $address"
                )
        val sortField = sortFieldForScope(scope)
        val balance = balanceForScope(doc, scope)
        val totalHolders =
            mongoTemplate.count(
                Query(Criteria.where(sortField).gt(decimalZero)),
                B3trBalance::class.java,
                collection,
            )
        if (balance.bigDecimalValue() <= BigDecimal.ZERO) {
            return B3trRankResponse(
                address = address,
                balance = BigInteger.ZERO,
                rank = totalHolders + 1,
                totalHolders = totalHolders,
                topPercentage = if (totalHolders > 0) 100.0 else 0.0,
            )
        }
        val rank =
            mongoTemplate.count(
                Query(Criteria.where(sortField).gt(balance)),
                B3trBalance::class.java,
                collection,
            ) + 1
        val topPercentage = if (totalHolders > 0) (rank.toDouble() / totalHolders) * 100 else 0.0
        return B3trRankResponse(
            address = address,
            balance = balance.bigDecimalValue().toBigInteger(),
            rank = rank,
            totalHolders = totalHolders,
            topPercentage = topPercentage,
        )
    }

    private fun sortFieldForScope(scope: RichlistScope): String =
        when (scope) {
            RichlistScope.ALL -> "totalBalance"
            RichlistScope.VOT3 -> "vot3Balance"
            RichlistScope.B3TR -> "b3trBalance"
        }

    private fun balanceForScope(doc: B3trBalance, scope: RichlistScope): Decimal128 =
        when (scope) {
            RichlistScope.ALL -> doc.totalBalance
            RichlistScope.VOT3 -> doc.vot3Balance
            RichlistScope.B3TR -> doc.b3trBalance
        }
}
