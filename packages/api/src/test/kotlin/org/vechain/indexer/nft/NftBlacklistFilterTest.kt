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
import org.springframework.data.mongodb.core.query.Criteria
import org.vechain.indexer.history.IndexedHistoryEvent
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class NftBlacklistFilterTest {
    private val mongoTemplate: MongoTemplate = mockk {
        every { getCollectionName(IndexedHistoryEvent::class.java) } returns "history_events"
    }

    @Test
    fun `sorts before the lookup and pages after it`() {
        val slot = slot<Aggregation>()
        every {
            mongoTemplate.aggregate(
                capture(slot),
                "history_events",
                IndexedHistoryEvent::class.java,
            )
        } returns AggregationResults(emptyList(), Document())
        val pageable = PageRequest.of(3, 20, Sort.by(Sort.Order.desc("blockTimestamp")))

        NftBlacklistFilter.findPage(
            mongoTemplate,
            Criteria.where("involvedAddresses").`is`("0xabc"),
            pageable,
            IndexedHistoryEvent::class.java,
        )

        val pipeline = slot.captured.toPipeline(Aggregation.DEFAULT_CONTEXT)
        expectThat(pipeline.map { it.keys.single() })
            .isEqualTo(
                listOf("\$match", "\$sort", "\$lookup", "\$match", "\$skip", "\$limit", "\$project")
            )
        expectThat(pipeline[2].get("\$lookup", Document::class.java).toJson())
            .isEqualTo(
                """{"from": "nft_blacklist", "localField": "contractAddress", "foreignField": "_id", "as": "blacklistInfo"}"""
            )
        expectThat(pipeline[3].get("\$match", Document::class.java).toJson())
            .isEqualTo("""{"blacklistInfo.isBlacklisted": {"${'$'}ne": true}}""")
        expectThat(pipeline[4]["\$skip"]).isEqualTo(60L)
        expectThat(pipeline[5]["\$limit"]).isEqualTo(21L)
        expectThat(pipeline[6].get("\$project", Document::class.java).toJson())
            .isEqualTo("""{"blacklistInfo": 0}""")
    }

    @Test
    fun `hasNext reflects the extra row fetched beyond the page`() {
        val pageable = PageRequest.of(0, 2, Sort.by(Sort.Order.desc("blockTimestamp")))
        val rows = (1..3).map { historyEvent("event-$it", it.toLong()) }
        every {
            mongoTemplate.aggregate(
                any<Aggregation>(),
                "history_events",
                IndexedHistoryEvent::class.java,
            )
        } returns AggregationResults(rows, Document())

        val full =
            NftBlacklistFilter.findPage(
                mongoTemplate,
                Criteria(),
                pageable,
                IndexedHistoryEvent::class.java,
            )
        expectThat(full.content).isEqualTo(rows.take(2))
        expectThat(full.hasNext()).isTrue()

        every {
            mongoTemplate.aggregate(
                any<Aggregation>(),
                "history_events",
                IndexedHistoryEvent::class.java,
            )
        } returns AggregationResults(rows.take(1), Document())
        val short =
            NftBlacklistFilter.findPage(
                mongoTemplate,
                Criteria(),
                pageable,
                IndexedHistoryEvent::class.java,
            )
        expectThat(short.hasNext()).isFalse()
    }

    private fun historyEvent(id: String, blockTimestamp: Long) =
        IndexedHistoryEvent(
            id = id,
            blockId = "block-$id",
            blockNumber = blockTimestamp,
            blockTimestamp = blockTimestamp,
            txId = "tx-$id",
            eventName = org.vechain.indexer.history.HistoryEventName.TRANSFER_NFT,
        )
}
