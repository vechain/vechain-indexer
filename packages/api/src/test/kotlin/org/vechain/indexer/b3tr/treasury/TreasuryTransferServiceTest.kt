package org.vechain.indexer.b3tr.treasury

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class TreasuryTransferServiceTest {

    private val mongoTemplate: MongoTemplate = mockk()
    private val repository: TreasuryTransferRepository = mockk()
    private val service = TreasuryTransferService(mongoTemplate, repository)

    private val pageable =
        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockTimestamp", "txId", "_id"))

    @Test
    fun `find builds empty criteria when no filters provided`() {
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), TreasuryTransfer::class.java) } returns
            listOf(transfer("t1"), transfer("t2"))

        val result = service.find(pageable = pageable)

        expectThat(result.content).hasSize(2)
        expectThat(result.hasNext()).isFalse()
        expectThat(querySlot.captured.queryObject.keys).hasSize(0)
    }

    @Test
    fun `find adds category criteria when category provided`() {
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), TreasuryTransfer::class.java) } returns
            listOf(transfer("t1", category = TreasuryTransferCategory.EMISSION))

        val result = service.find(category = TreasuryTransferCategory.EMISSION, pageable = pageable)

        expectThat(result.content).hasSize(1)
        val doc = querySlot.captured.queryObject
        expectThat(doc["category"].toString())
            .isEqualTo(TreasuryTransferCategory.EMISSION.toString())
    }

    @Test
    fun `find adds gte criteria when only after provided`() {
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), TreasuryTransfer::class.java) } returns
            emptyList()

        service.find(after = 1000L, pageable = pageable)

        val doc = querySlot.captured.queryObject
        val timestampDoc = doc["blockTimestamp"] as org.bson.Document
        expectThat(timestampDoc["\$gte"]).isEqualTo(1000L)
    }

    @Test
    fun `find adds lte criteria when only before provided`() {
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), TreasuryTransfer::class.java) } returns
            emptyList()

        service.find(before = 2000L, pageable = pageable)

        val doc = querySlot.captured.queryObject
        val timestampDoc = doc["blockTimestamp"] as org.bson.Document
        expectThat(timestampDoc["\$lte"]).isEqualTo(2000L)
    }

    @Test
    fun `find adds gte and lte criteria when both after and before provided`() {
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), TreasuryTransfer::class.java) } returns
            emptyList()

        service.find(after = 1000L, before = 2000L, pageable = pageable)

        val doc = querySlot.captured.queryObject
        val timestampDoc = doc["blockTimestamp"] as org.bson.Document
        expectThat(timestampDoc["\$gte"]).isEqualTo(1000L)
        expectThat(timestampDoc["\$lte"]).isEqualTo(2000L)
    }

    @Test
    fun `find combines category with time range criteria`() {
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), TreasuryTransfer::class.java) } returns
            emptyList()

        service.find(
            category = TreasuryTransferCategory.OUT,
            after = 500L,
            before = 900L,
            pageable = pageable,
        )

        val doc = querySlot.captured.queryObject
        expectThat(doc["category"].toString()).isEqualTo(TreasuryTransferCategory.OUT.toString())
        val timestampDoc = doc["blockTimestamp"] as org.bson.Document
        expectThat(timestampDoc["\$gte"]).isEqualTo(500L)
        expectThat(timestampDoc["\$lte"]).isEqualTo(900L)
    }

    @Test
    fun `hasNext is true when more results exist beyond pageSize`() {
        val small = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "blockTimestamp"))

        every { mongoTemplate.find(any<Query>(), TreasuryTransfer::class.java) } returns
            listOf(transfer("t1"), transfer("t2"), transfer("t3"))

        val result = service.find(pageable = small)

        expectThat(result.content).hasSize(2)
        expectThat(result.hasNext()).isTrue()
    }

    @Test
    fun `hasNext is false when results equal pageSize`() {
        val small = PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "blockTimestamp"))

        every { mongoTemplate.find(any<Query>(), TreasuryTransfer::class.java) } returns
            listOf(transfer("t1"), transfer("t2"), transfer("t3"))

        val result = service.find(pageable = small)

        expectThat(result.content).hasSize(3)
        expectThat(result.hasNext()).isFalse()
    }

    @Test
    fun `hasNext is false when fewer results than pageSize`() {
        every { mongoTemplate.find(any<Query>(), TreasuryTransfer::class.java) } returns
            listOf(transfer("t1"))

        val result = service.find(pageable = pageable)

        expectThat(result.content).hasSize(1)
        expectThat(result.hasNext()).isFalse()
    }

    @Test
    fun `getLatestIndexedBlocks returns block number from latest record`() {
        every { repository.getLatestRecord() } returns transfer("t1")

        val result = service.getLatestIndexedBlocks()

        expectThat(result["TreasuryTransfer"]).isEqualTo(100L)
    }

    @Test
    fun `getLatestIndexedBlocks returns 0 when no records exist`() {
        every { repository.getLatestRecord() } returns null

        val result = service.getLatestIndexedBlocks()

        expectThat(result["TreasuryTransfer"]).isEqualTo(0L)
    }

    private fun transfer(
        id: String,
        category: TreasuryTransferCategory = TreasuryTransferCategory.OTHER,
    ) =
        TreasuryTransfer(
            id = id,
            blockId = "0xblock",
            blockNumber = 100L,
            blockTimestamp = 1000L,
            txId = "0xtx-$id",
            from = "0xfrom",
            to = "0xto",
            value = "1000000000000000000",
            category = category,
            label = "test",
            counterpartyName = null,
        )
}
