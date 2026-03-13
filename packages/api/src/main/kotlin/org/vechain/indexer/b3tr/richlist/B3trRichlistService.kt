package org.vechain.indexer.b3tr.richlist

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
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

    fun getRichlist(
        size: Int?,
        direction: String?,
        cursor: String? = null,
        scope: RichlistScope = RichlistScope.ALL,
    ): PaginatedResponse<B3trRichlistItem> {
        val sortField = sortFieldForScope(scope)
        val pageSize = size ?: 20
        val sortDirection =
            if (direction?.uppercase() == "ASC") Sort.Direction.ASC else Sort.Direction.DESC
        val query =
            buildRichlistQuery(
                sortField = sortField,
                pageSize = pageSize,
                sortDirection = sortDirection,
                cursor = cursor,
            )

        val results = mongoTemplate.find(query, B3trBalance::class.java, collection)
        val page = results.take(pageSize)

        if (page.isEmpty()) {
            return paginatedResponse(data = emptyList(), hasNext = false, cursor = null)
        }

        val first = page.first()
        val startRank = countRankBefore(sortField, balanceForScope(first, scope), first.address) + 1

        val items =
            page.mapIndexed { index, doc ->
                B3trRichlistItem(
                    address = doc.address,
                    balance = balanceForScope(doc, scope),
                    rank = startRank + index,
                )
            }

        val nextCursor =
            if (results.size > pageSize) {
                val nextItem = results[pageSize - 1]
                CursorPaginationUtils.generateCursor(
                    balanceForScope(nextItem, scope),
                    nextItem.address,
                )
            } else {
                null
            }

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
                Query(Criteria.where(sortField).gt("0")),
                B3trBalance::class.java,
                collection,
            )
        if (balance <= BigInteger.ZERO) {
            return B3trRankResponse(
                address = address,
                balance = BigInteger.ZERO,
                rank = totalHolders + 1,
                totalHolders = totalHolders,
                topPercentage = if (totalHolders > 0) 100.0 else 0.0,
            )
        }
        val rank = countRankBefore(sortField, balance, address) + 1
        val topPercentage = if (totalHolders > 0) (rank.toDouble() / totalHolders) * 100 else 0.0
        return B3trRankResponse(
            address = address,
            balance = balance,
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

    private fun balanceForScope(doc: B3trBalance, scope: RichlistScope): BigInteger =
        when (scope) {
            RichlistScope.ALL -> doc.totalBalance
            RichlistScope.VOT3 -> doc.vot3Balance
            RichlistScope.B3TR -> doc.b3trBalance
        }

    private fun buildRichlistQuery(
        sortField: String,
        pageSize: Int,
        sortDirection: Sort.Direction,
        cursor: String?,
    ): Query {
        val criteria = mutableListOf<Criteria>()
        criteria.add(Criteria.where(sortField).gt(BigInteger.ZERO))

        CursorPaginationUtils.parseCursor(cursor)?.let { cursorInfo ->
            val cursorBalance = cursorInfo.sortValue.toBigInteger()
            val cursorAddress = cursorInfo.cursorValue
            val cursorCriteria =
                if (sortDirection == Sort.Direction.DESC) {
                    Criteria()
                        .orOperator(
                            Criteria.where(sortField).lt(cursorBalance),
                            Criteria.where(sortField)
                                .`is`(cursorBalance)
                                .and("_id")
                                .gt(cursorAddress),
                        )
                } else {
                    Criteria()
                        .orOperator(
                            Criteria.where(sortField).gt(cursorBalance),
                            Criteria.where(sortField)
                                .`is`(cursorBalance)
                                .and("_id")
                                .lt(cursorAddress),
                        )
                }
            criteria.add(cursorCriteria)
        }

        val query =
            if (criteria.size == 1) {
                Query(criteria.single())
            } else {
                Query(Criteria().andOperator(*criteria.toTypedArray()))
            }

        query.with(Sort.by(sortDirection, sortField).and(Sort.by(Sort.Direction.ASC, "_id")))
        query.limit(pageSize + 1)
        return query
    }

    private fun countRankBefore(sortField: String, balance: BigInteger, address: String): Long =
        mongoTemplate.count(
            Query(
                Criteria()
                    .andOperator(
                        Criteria.where(sortField).gt(BigInteger.ZERO),
                        Criteria()
                            .orOperator(
                                Criteria.where(sortField).gt(balance),
                                Criteria.where(sortField).`is`(balance).and("_id").lt(address),
                            ),
                    )
            ),
            B3trBalance::class.java,
            collection,
        )
}
