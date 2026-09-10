package org.vechain.indexer.nft

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.bson.Document
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.vechain.indexer.history.HistoryEventName
import org.vechain.indexer.history.IndexedHistoryEvent
import org.vechain.indexer.thor.Address
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class NftHistoryServiceTest {
    private val mongoTemplate: MongoTemplate = mockk {
        every { getCollectionName(IndexedHistoryEvent::class.java) } returns "history_events"
    }
    private val service = NftHistoryService(mongoTemplate)
    private val contractAddress = Address("0x1856c533ac2d94340aaa8544d35a5c1d4a21dee7")

    @Test
    fun `findTokenHistory builds contract and token scoped query with default events`() {
        val aggregationSlot = slot<Aggregation>()
        val pageable = PageRequest.of(0, 2, Sort.by(Sort.Order.desc("blockTimestamp")))
        val first = historyEvent("event-1", HistoryEventName.NFT_SALE, 300)
        val second = historyEvent("event-2", HistoryEventName.TRANSFER_NFT, 200)
        val third = historyEvent("event-3", HistoryEventName.NFT_SALE, 100)

        every {
            mongoTemplate.aggregate(
                capture(aggregationSlot),
                "history_events",
                IndexedHistoryEvent::class.java,
            )
        } returns AggregationResults(listOf(first, second, third), Document())

        val result =
            service.findTokenHistory(
                contractAddress = contractAddress,
                tokenId = "0x2a",
                eventNames = null,
                before = 500,
                after = 100,
                pageable = pageable,
            )

        val clauses = andClauses(matchStage(aggregationSlot.captured))
        val contractClause = clauses.single { it.containsKey("contractAddress") }
        val tokenClause = clauses.single { it.containsKey("tokenId") }
        val eventClause = clauses.single { it.containsKey("eventName") }
        val timestampClause = clauses.single { it.containsKey("blockTimestamp") }
        val requestedEvents =
            eventClause.get("eventName", Document::class.java).getList("\$in", String::class.java)

        expectThat(contractClause["contractAddress"]).isEqualTo(contractAddress.value)
        expectThat(tokenClause["tokenId"]).isEqualTo("42")
        expectThat(requestedEvents).containsExactly("TRANSFER_NFT", "NFT_SALE")
        expectThat(timestampClause.get("blockTimestamp", Document::class.java).toJson())
            .isEqualTo("""{"${'$'}gte": 100, "${'$'}lte": 500}""")
        expectThat(stageNames(aggregationSlot.captured))
            .isEqualTo(
                listOf("\$match", "\$sort", "\$lookup", "\$match", "\$skip", "\$limit", "\$project")
            )

        expectThat(result.content).containsExactly(first, second)
        expectThat(result.hasNext()).isTrue()
    }

    @Test
    fun `findTokenHistory applies explicit event filter`() {
        val aggregationSlot = slot<Aggregation>()
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("blockTimestamp")))

        every {
            mongoTemplate.aggregate(
                capture(aggregationSlot),
                "history_events",
                IndexedHistoryEvent::class.java,
            )
        } returns AggregationResults(emptyList(), Document())

        service.findTokenHistory(
            contractAddress = contractAddress,
            tokenId = "42",
            eventNames = listOf("NFT_SALE"),
            before = null,
            after = null,
            pageable = pageable,
        )

        val clauses = andClauses(matchStage(aggregationSlot.captured))
        val eventClause = clauses.single { it.containsKey("eventName") }
        val requestedEvents =
            eventClause.get("eventName", Document::class.java).getList("\$in", String::class.java)

        expectThat(requestedEvents).containsExactly("NFT_SALE")
    }

    @Test
    fun `findTokenHistory reports no next page when raw results fit within page size`() {
        val pageable = PageRequest.of(0, 2, Sort.by(Sort.Order.desc("blockTimestamp")))
        val only = historyEvent("event-1", HistoryEventName.TRANSFER_NFT, 100)

        every {
            mongoTemplate.aggregate(
                any<Aggregation>(),
                "history_events",
                IndexedHistoryEvent::class.java,
            )
        } returns AggregationResults(listOf(only), Document())

        val result =
            service.findTokenHistory(
                contractAddress = contractAddress,
                tokenId = "1",
                eventNames = null,
                before = null,
                after = null,
                pageable = pageable,
            )

        expectThat(result.content).containsExactly(only)
        expectThat(result.hasNext()).isFalse()
    }

    private fun historyEvent(id: String, eventName: HistoryEventName, blockTimestamp: Long) =
        IndexedHistoryEvent(
            id = id,
            blockId = "block-$id",
            blockNumber = blockTimestamp,
            blockTimestamp = blockTimestamp,
            txId = "tx-$id",
            contractAddress = contractAddress.value,
            tokenId = "42",
            eventName = eventName,
        )

    private fun matchStage(aggregation: Aggregation): Document =
        aggregation
            .toPipeline(Aggregation.DEFAULT_CONTEXT)
            .first()
            .get("\$match", Document::class.java)

    private fun stageNames(aggregation: Aggregation): List<String> =
        aggregation.toPipeline(Aggregation.DEFAULT_CONTEXT).map { it.keys.single() }

    @Suppress("UNCHECKED_CAST")
    private fun andClauses(document: Document): List<Document> =
        document["\$and"] as? List<Document>
            ?: error("Expected top-level \$and in query: $document")
}
