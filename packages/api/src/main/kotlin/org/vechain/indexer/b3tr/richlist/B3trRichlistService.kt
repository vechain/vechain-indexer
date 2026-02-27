package org.vechain.indexer.b3tr.richlist

import java.math.BigInteger
import org.bson.Document
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import org.springframework.data.mongodb.core.aggregation.AggregationOperationContext
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.b3tr.balance.B3trBalance
import org.vechain.indexer.b3tr.balance.repository.B3trBalanceRepository
import org.vechain.indexer.b3tr.richlist.response.B3trRankResponse
import org.vechain.indexer.b3tr.richlist.response.B3trRichlistItem
import org.vechain.indexer.b3tr.vot3.Vot3Balance
import org.vechain.indexer.b3tr.vot3.repository.Vot3BalanceRepository
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.utils.CursorPaginationUtils

@Profile("b3tr", "vot3-balance", "b3tr-balance")
@Service
open class B3trRichlistService(
    private val mongoTemplate: MongoTemplate,
    private val vot3Repository: Vot3BalanceRepository,
    private val b3trRepository: B3trBalanceRepository,
) {

    private val vot3Collection = IndexerNames.VOT3_BALANCE.COLLECTION
    private val b3trCollection = IndexerNames.B3TR_BALANCE.COLLECTION

    fun getRichlist(
        size: Int?,
        direction: String?,
        cursor: String? = null,
        scope: RichlistScope = RichlistScope.ALL,
    ): PaginatedResponse<B3trRichlistItem> =
        when (scope) {
            RichlistScope.ALL -> getRichlistMerged(size, direction, cursor)
            RichlistScope.VOT3 ->
                getRichlistSingleCollection(
                    vot3Collection,
                    Vot3Balance::class.java,
                    size,
                    direction,
                    cursor,
                )
            RichlistScope.B3TR ->
                getRichlistSingleCollection(
                    b3trCollection,
                    B3trBalance::class.java,
                    size,
                    direction,
                    cursor,
                )
        }

    fun getAddressRank(
        address: String,
        scope: RichlistScope = RichlistScope.ALL,
    ): B3trRankResponse =
        when (scope) {
            RichlistScope.ALL -> getAddressRankMerged(address)
            RichlistScope.VOT3 ->
                getAddressRankSingleCollection(
                    address,
                    vot3Collection,
                    { vot3Repository.findById(it).orElse(null) },
                    Vot3Balance::balance.name,
                )
            RichlistScope.B3TR ->
                getAddressRankSingleCollection(
                    address,
                    b3trCollection,
                    { b3trRepository.findById(it).orElse(null) },
                    B3trBalance::balance.name,
                )
        }

    private fun getRichlistMerged(
        size: Int?,
        direction: String?,
        cursor: String?,
    ): PaginatedResponse<B3trRichlistItem> {
        val pageSize = size ?: 20
        val sortDesc = direction?.uppercase() != "ASC"
        val cursorInfo = CursorPaginationUtils.parseCursor(cursor)
        val cursorCombined =
            cursorInfo?.let { CursorPaginationUtils.parseSortValue(it.sortValue) as? Long }
        val cursorAddress = cursorInfo?.cursorValue

        val pipeline = buildMergedPipeline(cursorCombined, cursorAddress, sortDesc, pageSize + 1)
        val aggregation = Aggregation.newAggregation(pipeline)
        val results =
            mongoTemplate.aggregate(aggregation, vot3Collection, Document::class.java).mappedResults

        val page = results.take(pageSize)
        if (page.isEmpty()) {
            return paginatedResponse(data = emptyList(), hasNext = false, cursor = null)
        }

        val first = page.first()
        val firstCombined = first.getLong("combined") ?: 0L
        val firstAddress =
            first.getString("_id")
                ?: return paginatedResponse(data = emptyList(), hasNext = false, cursor = null)
        val startRank = countCombinedAbove(firstCombined, firstAddress, sortDesc) + 1

        val items =
            page.mapIndexed { index, doc ->
                B3trRichlistItem(
                    address = doc.getString("_id") ?: "",
                    balance = BigInteger.valueOf(doc.getLong("combined") ?: 0L),
                    rank = startRank + index,
                )
            }

        val nextCursor =
            if (results.size > pageSize) {
                val last = results[pageSize - 1]
                val lastCombined = last.getLong("combined")
                val lastId = last.getString("_id")
                if (lastCombined != null && lastId != null) {
                    CursorPaginationUtils.generateCursor(lastCombined, lastId)
                } else null
            } else null

        return paginatedResponse(
            data = items,
            hasNext = results.size > pageSize,
            cursor = nextCursor,
        )
    }

    private fun <T> getRichlistSingleCollection(
        collection: String,
        entityClass: Class<T>,
        size: Int?,
        direction: String?,
        cursor: String?,
    ): PaginatedResponse<B3trRichlistItem> {
        val criteria = Criteria.where("balance").gt("0")
        val (pageSize, query) =
            CursorPaginationUtils.buildCursorQuery(
                baseCriteria = criteria,
                size = size,
                direction = direction,
                sortByField = "balance",
                cursor = cursor,
                cursorField = "_id",
            )

        val results = mongoTemplate.find(query, entityClass, collection)
        val page = results.take(pageSize)

        if (page.isEmpty()) {
            return paginatedResponse(data = emptyList(), hasNext = false, cursor = null)
        }

        val firstBalance = page.first()
        val firstBalanceStr = getBalanceString(firstBalance as Any)
        val higherCount =
            mongoTemplate.count(
                Query(Criteria.where("balance").gt(firstBalanceStr)),
                entityClass,
                collection,
            )
        val startRank = higherCount + 1

        val items =
            page.mapIndexed { index, doc ->
                val address = getAddress(doc as Any)
                val balance = getBalance(doc as Any)
                B3trRichlistItem(address = address, balance = balance, rank = startRank + index)
            }

        val nextCursor =
            CursorPaginationUtils.calculateNextCursor(
                results = results,
                pageSize = pageSize,
                sortByField = "balance",
                cursorField = "address",
            )

        return paginatedResponse(
            data = items,
            hasNext = results.size > pageSize,
            cursor = nextCursor,
        )
    }

    private fun getAddressRankMerged(address: String): B3trRankResponse {
        val vot3Balance = vot3Repository.findById(address).orElse(null)
        val b3trBalance = b3trRepository.findById(address).orElse(null)
        val combinedBi =
            (vot3Balance?.balance ?: BigInteger.ZERO) + (b3trBalance?.balance ?: BigInteger.ZERO)
        if (combinedBi <= BigInteger.ZERO) {
            throw ResourceNotFoundException("Address not found in B3TR/VOT3 holders: $address")
        }
        val combined = safeToLong(combinedBi)
        val rank = countCombinedRank(combined, address)
        val total = countCombinedTotal()
        val topPct = if (total > 0) (rank.toDouble() / total) * 100 else 0.0
        return B3trRankResponse(
            address = address,
            balance = combinedBi,
            rank = rank,
            totalHolders = total,
            topPercentage = topPct,
        )
    }

    private fun getAddressRankSingleCollection(
        address: String,
        collection: String,
        findById: (String) -> Any?,
        balanceField: String,
    ): B3trRankResponse {
        val doc =
            findById(address)
                ?: throw ResourceNotFoundException("Address not found in holders: $address")
        val balance = getBalance(doc)
        if (balance <= BigInteger.ZERO) {
            throw ResourceNotFoundException("Address has no balance: $address")
        }
        val balanceStr = balance.toString()
        val higherCount =
            mongoTemplate.count(
                Query(Criteria.where(balanceField).gt(balanceStr)),
                Document::class.java,
                collection,
            )
        val rank = higherCount + 1
        val total =
            mongoTemplate.count(
                Query(Criteria.where("balance").gt("0")),
                Document::class.java,
                collection,
            )
        val topPct = if (total > 0) (rank.toDouble() / total) * 100 else 0.0
        return B3trRankResponse(
            address = address,
            balance = balance,
            rank = rank,
            totalHolders = total,
            topPercentage = topPct,
        )
    }

    private fun getAddress(doc: Any): String =
        when (doc) {
            is Vot3Balance -> doc.address
            is B3trBalance -> doc.address
            else ->
                (doc::class.java.getMethod("getAddress").invoke(doc)
                        ?: doc::class.java.getMethod("getDocumentId").invoke(doc))
                    .toString()
        }

    private fun getBalance(doc: Any): BigInteger =
        when (doc) {
            is Vot3Balance -> doc.balance
            is B3trBalance -> doc.balance
            else -> (doc::class.java.getMethod("getBalance").invoke(doc) as BigInteger)
        }

    private fun getBalanceString(doc: Any): String = getBalance(doc).toString()

    private fun buildMergedPipeline(
        cursorCombined: Long?,
        cursorAddress: String?,
        sortDesc: Boolean,
        limit: Int,
    ): List<AggregationOperation> {
        val stages = mutableListOf<AggregationOperation>()

        val balanceToLong =
            Document(
                "\$convert",
                Document()
                    .append("input", "\$balance")
                    .append("to", "long")
                    .append("onError", 0L)
                    .append("onNull", 0L),
            )
        val zeroLong = Document("\$literal", 0L)
        stages.add(
            rawStage(
                Document(
                    "\$project",
                    Document()
                        .append("_id", "\$_id")
                        .append("vot3Long", balanceToLong)
                        .append("b3trLong", zeroLong),
                )
            )
        )
        stages.add(
            rawStage(
                Document(
                    "\$unionWith",
                    Document()
                        .append("coll", b3trCollection)
                        .append(
                            "pipeline",
                            listOf(
                                Document(
                                    "\$project",
                                    Document()
                                        .append("_id", "\$_id")
                                        .append("vot3Long", zeroLong)
                                        .append("b3trLong", balanceToLong),
                                )
                            ),
                        ),
                )
            )
        )
        stages.add(
            rawStage(
                Document(
                    "\$group",
                    Document()
                        .append("_id", "\$_id")
                        .append("vot3", Document("\$sum", "\$vot3Long"))
                        .append("b3tr", Document("\$sum", "\$b3trLong")),
                )
            )
        )
        stages.add(
            rawStage(
                Document(
                    "\$addFields",
                    Document("combined", Document("\$add", listOf("\$vot3", "\$b3tr"))),
                )
            )
        )
        stages.add(rawStage(Document("\$match", Document("combined", Document("\$gt", 0)))))

        if (cursorCombined != null && cursorAddress != null) {
            val cursorMatch =
                Document(
                    "\$or",
                    listOf(
                        Document(
                            "combined",
                            if (sortDesc) Document("\$lt", cursorCombined)
                            else Document("\$gt", cursorCombined),
                        ),
                        Document("combined", cursorCombined)
                            .append(
                                "_id",
                                if (sortDesc) Document("\$gt", cursorAddress)
                                else Document("\$lt", cursorAddress),
                            ),
                    ),
                )
            stages.add(rawStage(Document("\$match", cursorMatch)))
        }

        stages.add(
            rawStage(
                Document(
                    "\$sort",
                    Document().append("combined", if (sortDesc) -1 else 1).append("_id", 1),
                )
            )
        )
        stages.add(rawStage(Document("\$limit", limit)))

        return stages
    }

    private fun countCombinedAbove(combined: Long, address: String, sortDesc: Boolean): Long {
        val pipeline = buildMergedPipeline(null, null, sortDesc, Int.MAX_VALUE)
        val aboveCriteria =
            Document(
                "\$or",
                listOf(
                    Document(
                        "combined",
                        if (sortDesc) Document("\$gt", combined) else Document("\$lt", combined),
                    ),
                    Document("combined", combined)
                        .append(
                            "_id",
                            if (sortDesc) Document("\$lt", address) else Document("\$gt", address),
                        ),
                ),
            )
        val countStages =
            pipeline.dropLast(1) +
                listOf(
                    rawStage(Document("\$match", aboveCriteria)),
                    rawStage(Document("\$count", "n")),
                )
        val agg = Aggregation.newAggregation(countStages)
        val out =
            mongoTemplate.aggregate(agg, vot3Collection, Document::class.java).uniqueMappedResult
                ?: return 0
        return out.getInteger("n", 0).toLong()
    }

    private fun countCombinedTotal(): Long {
        val pipeline =
            buildMergedPipeline(null, null, true, Int.MAX_VALUE).dropLast(1) +
                listOf(rawStage(Document("\$count", "n")))
        val agg = Aggregation.newAggregation(pipeline)
        val out =
            mongoTemplate.aggregate(agg, vot3Collection, Document::class.java).uniqueMappedResult
                ?: return 0
        return out.getInteger("n", 0).toLong()
    }

    private fun countCombinedRank(combined: Long, address: String): Long =
        countCombinedAbove(combined, address, sortDesc = true) + 1

    private fun safeToLong(value: BigInteger): Long {
        if (value > BigInteger.valueOf(Long.MAX_VALUE)) return Long.MAX_VALUE
        if (value < BigInteger.valueOf(Long.MIN_VALUE)) return Long.MIN_VALUE
        return value.toLong()
    }

    private fun rawStage(doc: Document): AggregationOperation =
        object : AggregationOperation {
            override fun toDocument(context: AggregationOperationContext): Document = doc
        }
}
