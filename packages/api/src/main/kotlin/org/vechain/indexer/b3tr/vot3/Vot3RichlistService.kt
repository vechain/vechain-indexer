package org.vechain.indexer.b3tr.vot3

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.b3tr.vot3.repository.Vot3BalanceRepository
import org.vechain.indexer.b3tr.vot3.response.Vot3RankResponse
import org.vechain.indexer.b3tr.vot3.response.Vot3RichlistItem
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.utils.CursorPaginationUtils.buildCursorQuery
import org.vechain.indexer.utils.CursorPaginationUtils.calculateNextCursor

@Profile("b3tr", "vot3-balance")
@Service
open class Vot3RichlistService(
    private val mongoTemplate: MongoTemplate,
    private val repository: Vot3BalanceRepository,
) {

    fun getRichlist(
        size: Int?,
        direction: String?,
        cursor: String? = null,
    ): PaginatedResponse<Vot3RichlistItem> {
        val collectionName = IndexerNames.VOT3_BALANCE.COLLECTION
        val criteria = Criteria.where(Vot3Balance::balance.name).gt("0")
        val (pageSize, query) =
            buildCursorQuery(
                baseCriteria = criteria,
                size = size,
                direction = direction,
                sortByField = Vot3Balance::balance.name,
                cursor = cursor,
                cursorField = "_id",
            )

        val results = mongoTemplate.find(query, Vot3Balance::class.java, collectionName)
        val page = results.take(pageSize)

        val startRank =
            if (page.isEmpty()) {
                1L
            } else {
                val firstBalance = page.first().balance
                val higherCount =
                    mongoTemplate.count(
                        Query(
                            Criteria.where(Vot3Balance::balance.name).gt(firstBalance.toString())
                        ),
                        Vot3Balance::class.java,
                        collectionName,
                    )
                higherCount + 1
            }

        val items =
            page.mapIndexed { index, balance -> Vot3RichlistItem.from(balance, startRank + index) }

        val nextCursor =
            calculateNextCursor(
                results = results,
                pageSize = pageSize,
                sortByField = Vot3Balance::balance.name,
                cursorField = "address",
            )

        return paginatedResponse(
            data = items,
            hasNext = results.size > pageSize,
            cursor = nextCursor,
        )
    }

    fun getAddressRank(address: String): Vot3RankResponse {
        val userBalance =
            repository.findById(address).orElse(null)
                ?: throw ResourceNotFoundException("Address not found in VOT3 holders: $address")

        if (userBalance.balance <= BigInteger.ZERO) {
            throw ResourceNotFoundException("Address has no VOT3 balance: $address")
        }

        val collectionName = IndexerNames.VOT3_BALANCE.COLLECTION
        val higherCount =
            mongoTemplate.count(
                Query(Criteria.where(Vot3Balance::balance.name).gt(userBalance.balance.toString())),
                Vot3Balance::class.java,
                collectionName,
            )
        val rank = higherCount + 1

        val totalHolders =
            mongoTemplate.count(
                Query(Criteria.where(Vot3Balance::balance.name).gt("0")),
                Vot3Balance::class.java,
                collectionName,
            )
        val topPercentage = if (totalHolders > 0) (rank.toDouble() / totalHolders) * 100 else 0.0

        return Vot3RankResponse(
            address = address,
            balance = userBalance.balance,
            rank = rank,
            totalHolders = totalHolders,
            topPercentage = topPercentage,
        )
    }
}
