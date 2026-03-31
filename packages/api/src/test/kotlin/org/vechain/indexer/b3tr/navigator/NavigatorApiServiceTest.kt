package org.vechain.indexer.b3tr.navigator

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

class NavigatorApiServiceTest {

    private val mongoTemplate: MongoTemplate = mockk()
    private val service = NavigatorApiService(mongoTemplate)

    private val pageable =
        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockTimestamp", "txId", "_id"))

    // -- findEvents --

    @Test
    fun `findEvents builds empty criteria when no filters provided`() {
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), NavigatorEvent::class.java) } returns
            listOf(event("e1"), event("e2"))

        val result = service.findEvents(pageable = pageable)

        expectThat(result.content).hasSize(2)
        expectThat(result.hasNext()).isFalse()
        expectThat(querySlot.captured.queryObject.keys).hasSize(0)
    }

    @Test
    fun `findEvents adds navigator criteria when provided`() {
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), NavigatorEvent::class.java) } returns
            listOf(event("e1"))

        val result = service.findEvents(navigator = "0xNav1", pageable = pageable)

        expectThat(result.content).hasSize(1)
        val doc = querySlot.captured.queryObject
        expectThat(doc["navigator"].toString()).isEqualTo("0xnav1")
    }

    @Test
    fun `findEvents adds eventType criteria when provided`() {
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), NavigatorEvent::class.java) } returns
            listOf(event("e1"))

        val result = service.findEvents(eventType = "NavigatorRegistered", pageable = pageable)

        expectThat(result.content).hasSize(1)
        val doc = querySlot.captured.queryObject
        expectThat(doc["eventType"].toString()).isEqualTo("NavigatorRegistered")
    }

    @Test
    fun `findEvents adds gte criteria when only after provided`() {
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), NavigatorEvent::class.java) } returns
            emptyList()

        service.findEvents(after = 1000L, pageable = pageable)

        val doc = querySlot.captured.queryObject
        val timestampDoc = doc["blockTimestamp"] as org.bson.Document
        expectThat(timestampDoc["\$gte"]).isEqualTo(1000L)
    }

    @Test
    fun `findEvents adds gte and lte criteria when both provided`() {
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), NavigatorEvent::class.java) } returns
            emptyList()

        service.findEvents(after = 1000L, before = 2000L, pageable = pageable)

        val doc = querySlot.captured.queryObject
        val timestampDoc = doc["blockTimestamp"] as org.bson.Document
        expectThat(timestampDoc["\$gte"]).isEqualTo(1000L)
        expectThat(timestampDoc["\$lte"]).isEqualTo(2000L)
    }

    @Test
    fun `findEvents hasNext true when more results exist`() {
        val small = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "blockTimestamp"))

        every { mongoTemplate.find(any<Query>(), NavigatorEvent::class.java) } returns
            listOf(event("e1"), event("e2"), event("e3"))

        val result = service.findEvents(pageable = small)

        expectThat(result.content).hasSize(2)
        expectThat(result.hasNext()).isTrue()
    }

    @Test
    fun `findEvents hasNext false when results fit page`() {
        every { mongoTemplate.find(any<Query>(), NavigatorEvent::class.java) } returns
            listOf(event("e1"))

        val result = service.findEvents(pageable = pageable)

        expectThat(result.content).hasSize(1)
        expectThat(result.hasNext()).isFalse()
    }

    // -- findDelegations --

    @Test
    fun `findDelegations adds citizen criteria when provided`() {
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), NavigatorDelegation::class.java) } returns
            listOf(delegation("d1"))

        val result = service.findDelegations(citizen = "0xCitizen1", pageable = pageable)

        expectThat(result.content).hasSize(1)
        val doc = querySlot.captured.queryObject
        expectThat(doc["citizen"].toString()).isEqualTo("0xcitizen1")
    }

    @Test
    fun `findDelegations adds navigator criteria when provided`() {
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), NavigatorDelegation::class.java) } returns
            listOf(delegation("d1"))

        val result = service.findDelegations(navigator = "0xNav1", pageable = pageable)

        expectThat(result.content).hasSize(1)
        val doc = querySlot.captured.queryObject
        expectThat(doc["navigator"].toString()).isEqualTo("0xnav1")
    }

    @Test
    fun `findDelegations combines citizen and navigator filters`() {
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), NavigatorDelegation::class.java) } returns
            emptyList()

        service.findDelegations(citizen = "0xCitizen1", navigator = "0xNav1", pageable = pageable)

        val doc = querySlot.captured.queryObject
        expectThat(doc["citizen"].toString()).isEqualTo("0xcitizen1")
        expectThat(doc["navigator"].toString()).isEqualTo("0xnav1")
    }

    // -- findFees --

    @Test
    fun `findFees adds navigator criteria when provided`() {
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), NavigatorFee::class.java) } returns
            listOf(fee("f1"))

        val result = service.findFees(navigator = "0xNav1", pageable = pageable)

        expectThat(result.content).hasSize(1)
        val doc = querySlot.captured.queryObject
        expectThat(doc["navigator"].toString()).isEqualTo("0xnav1")
    }

    @Test
    fun `findFees builds empty criteria when no filters provided`() {
        val querySlot = slot<Query>()

        every { mongoTemplate.find(capture(querySlot), NavigatorFee::class.java) } returns
            listOf(fee("f1"), fee("f2"))

        val result = service.findFees(pageable = pageable)

        expectThat(result.content).hasSize(2)
        expectThat(querySlot.captured.queryObject.keys).hasSize(0)
    }

    // -- helpers --

    private fun event(id: String, eventType: String = "NavigatorRegistered") =
        NavigatorEvent(
            id = id,
            blockId = "0xblock",
            blockNumber = 100L,
            blockTimestamp = 1000L,
            txId = "0xtx-$id",
            navigator = "0xnav",
            eventType = eventType,
            stakeAmount = null,
            metadataURI = null,
            slashAmount = null,
            slashReason = null,
            remainingStake = null,
            announcedAtRound = null,
            effectiveRound = null,
            reportRoundId = null,
            reportURI = null,
        )

    private fun delegation(id: String, eventType: String = "DelegationCreated") =
        NavigatorDelegation(
            id = id,
            blockId = "0xblock",
            blockNumber = 100L,
            blockTimestamp = 1000L,
            txId = "0xtx-$id",
            citizen = "0xcitizen",
            navigator = "0xnav",
            eventType = eventType,
            amount = "1000",
            roundId = null,
            appIds = null,
            voteWeights = null,
        )

    private fun fee(id: String, eventType: String = "FeeDeposited") =
        NavigatorFee(
            id = id,
            blockId = "0xblock",
            blockNumber = 100L,
            blockTimestamp = 1000L,
            txId = "0xtx-$id",
            navigator = "0xnav",
            eventType = eventType,
            roundId = "1",
            amount = "1000",
            citizen = null,
        )
}
