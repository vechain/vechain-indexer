package org.vechain.indexer.b3tr.richlist

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import java.math.BigInteger
import java.util.Optional
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.b3tr.balance.B3trBalance
import org.vechain.indexer.b3tr.balance.repository.B3trBalanceRepository
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.exception.ResourceNotFoundException

@ExtendWith(MockKExtension::class)
internal class B3trRichlistServiceTest {

    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var b3trRepository: B3trBalanceRepository

    private lateinit var service: B3trRichlistService

    @BeforeEach
    fun setUp() {
        service = B3trRichlistService(mongoTemplate, b3trRepository)
    }

    @Test
    fun `getAddressRank ALL uses totalBalance and computes rank and topPercentage`() {
        val address = "0xaddr0000000000000000000000000000000001"
        val doc =
            B3trBalance(
                address = address,
                blockId = "0xblock",
                blockNumber = 10L,
                blockTimestamp = 1000L,
                version = 1,
                vot3Balance = BigInteger("100"),
                b3trBalance = BigInteger("50"),
                totalBalance = BigInteger("150"),
            )
        val countQueries = mutableListOf<Query>()
        every { b3trRepository.findById(address) } returns Optional.of(doc)
        every { mongoTemplate.count(capture(countQueries), any(), any<String>()) } returnsMany
            listOf(100L, 4L)

        val result = service.getAddressRank(address, RichlistScope.ALL)

        assertEquals(address, result.address)
        assertEquals(BigInteger("150"), result.balance)
        assertEquals(5, result.rank)
        assertEquals(100, result.totalHolders)
        assertEquals(5.0, result.topPercentage)
        assertEquals(
            BigInteger.ZERO,
            (countQueries.first().queryObject["totalBalance"] as Document)["\$gt"],
        )
    }

    @Test
    fun `getAddressRank uses address tie breaker for equal balances`() {
        val address = "0xb000000000000000000000000000000000000000"
        val doc =
            B3trBalance(
                address = address,
                blockId = "0xblock",
                blockNumber = 10L,
                blockTimestamp = 1000L,
                version = 1,
                vot3Balance = BigInteger.ZERO,
                b3trBalance = BigInteger("75"),
                totalBalance = BigInteger("75"),
            )
        val countQueries = mutableListOf<Query>()
        every { b3trRepository.findById(address) } returns Optional.of(doc)
        every { mongoTemplate.count(capture(countQueries), any(), any<String>()) } returnsMany
            listOf(80L, 3L)

        val result = service.getAddressRank(address, RichlistScope.B3TR)

        assertEquals(4L, result.rank)
        val rankAndCriteria = countQueries[1].queryObject["\$and"] as List<*>
        val positionCriteria = (rankAndCriteria[1] as Document)["\$or"] as List<*>
        val equalBalanceCriteria = positionCriteria[1] as Document
        assertEquals(BigInteger("75"), equalBalanceCriteria["b3trBalance"])
        assertEquals(address, (equalBalanceCriteria["_id"] as Document)["\$lt"])
    }

    @Test
    fun `getAddressRank VOT3 uses vot3Balance`() {
        val address = "0xaddr0000000000000000000000000000000001"
        val doc =
            B3trBalance(
                address = address,
                blockId = "0xblock",
                blockNumber = 10L,
                blockTimestamp = 1000L,
                version = 1,
                vot3Balance = BigInteger("200"),
                b3trBalance = BigInteger("0"),
                totalBalance = BigInteger("200"),
            )
        every { b3trRepository.findById(address) } returns Optional.of(doc)
        var countInvocations = 0
        every { mongoTemplate.count(any<Query>(), any(), any<String>()) } answers
            {
                countInvocations++
                if (countInvocations == 1) 50L else 1L
            }

        val result = service.getAddressRank(address, RichlistScope.VOT3)

        assertEquals(BigInteger("200"), result.balance)
        assertEquals(2, result.rank)
        assertEquals(50, result.totalHolders)
    }

    @Test
    fun `getAddressRank B3TR uses b3trBalance`() {
        val address = "0xaddr0000000000000000000000000000000001"
        val doc =
            B3trBalance(
                address = address,
                blockId = "0xblock",
                blockNumber = 10L,
                blockTimestamp = 1000L,
                version = 1,
                vot3Balance = BigInteger.ZERO,
                b3trBalance = BigInteger("75"),
                totalBalance = BigInteger("75"),
            )
        every { b3trRepository.findById(address) } returns Optional.of(doc)
        var countInvocations = 0
        every { mongoTemplate.count(any<Query>(), any(), any<String>()) } answers
            {
                countInvocations++
                if (countInvocations == 1) 80L else 3L
            }

        val result = service.getAddressRank(address, RichlistScope.B3TR)

        assertEquals(BigInteger("75"), result.balance)
        assertEquals(4, result.rank)
        assertEquals(80, result.totalHolders)
    }

    @Test
    fun `getAddressRank throws when address not found`() {
        val address = "0xmissing00000000000000000000000000000001"
        every { b3trRepository.findById(address) } returns Optional.empty()

        assertThrows(ResourceNotFoundException::class.java) {
            service.getAddressRank(address, RichlistScope.ALL)
        }
    }

    @Test
    fun `getAddressRank returns zero balance and rank when address has zero for scope`() {
        val address = "0xaddr0000000000000000000000000000000001"
        val doc =
            B3trBalance(
                address = address,
                blockId = "0xblock",
                blockNumber = 10L,
                blockTimestamp = 1000L,
                version = 1,
                vot3Balance = BigInteger.ZERO,
                b3trBalance = BigInteger.ZERO,
                totalBalance = BigInteger.ZERO,
            )
        every { b3trRepository.findById(address) } returns Optional.of(doc)
        every { mongoTemplate.count(any<Query>(), any(), any<String>()) } returns 42

        val result = service.getAddressRank(address, RichlistScope.ALL)

        assertEquals(address, result.address)
        assertEquals(BigInteger.ZERO, result.balance)
        assertEquals(43, result.rank)
        assertEquals(42, result.totalHolders)
        assertEquals(100.0, result.topPercentage)
    }

    @Test
    fun `getRichlist empty returns empty paginated response`() {
        every { mongoTemplate.find(any<Query>(), any<Class<*>>(), any<String>()) } returns
            emptyList()

        val result = service.getRichlist(size = 20, direction = "DESC", scope = RichlistScope.ALL)

        assertEquals(0, result.data.size)
        assertEquals(false, result.pagination.hasNext)
        assertEquals(null, result.pagination.cursor)
    }

    @Test
    fun `getRichlist with data returns items with balance by scope`() {
        val alice =
            B3trBalance(
                address = "0xalice000000000000000000000000000000001",
                blockId = "0xb",
                blockNumber = 2L,
                blockTimestamp = 2000L,
                version = 1,
                vot3Balance = BigInteger("100"),
                b3trBalance = BigInteger("50"),
                totalBalance = BigInteger("150"),
            )
        val bob =
            B3trBalance(
                address = "0xbob000000000000000000000000000000002",
                blockId = "0xb",
                blockNumber = 2L,
                blockTimestamp = 2000L,
                version = 1,
                vot3Balance = BigInteger("80"),
                b3trBalance = BigInteger("20"),
                totalBalance = BigInteger("100"),
            )
        every { mongoTemplate.find(any<Query>(), any<Class<*>>(), any<String>()) } returns
            listOf(alice, bob)
        every { mongoTemplate.count(any<Query>(), any<Class<*>>(), any<String>()) } returns 0

        val result = service.getRichlist(size = 20, direction = "DESC", scope = RichlistScope.ALL)

        assertEquals(2, result.data.size)
        assertEquals(BigInteger("150"), result.data[0].balance)
        assertEquals(BigInteger("100"), result.data[1].balance)
        assertEquals(1, result.data[0].rank)
        assertEquals(2, result.data[1].rank)
    }

    @Test
    fun `getRichlist uses numeric cursor values and tie breaker for equal balances`() {
        val querySlot = slot<Query>()
        val countQueries = mutableListOf<Query>()
        val bob =
            B3trBalance(
                address = "0xb000000000000000000000000000000000000000",
                blockId = "0xb",
                blockNumber = 2L,
                blockTimestamp = 2000L,
                version = 1,
                vot3Balance = BigInteger("60"),
                b3trBalance = BigInteger("40"),
                totalBalance = BigInteger("100"),
            )
        val carol =
            B3trBalance(
                address = "0xc000000000000000000000000000000000000000",
                blockId = "0xb",
                blockNumber = 2L,
                blockTimestamp = 2000L,
                version = 1,
                vot3Balance = BigInteger("60"),
                b3trBalance = BigInteger("40"),
                totalBalance = BigInteger("100"),
            )
        every { mongoTemplate.find(capture(querySlot), any<Class<*>>(), any<String>()) } returns
            listOf(bob, carol)
        every { mongoTemplate.count(capture(countQueries), any<Class<*>>(), any<String>()) } returns
            1L

        val result =
            service.getRichlist(
                size = 1,
                direction = "DESC",
                cursor = "100|0xafffffffffffffffffffffffffffffffffffffff",
                scope = RichlistScope.ALL,
            )

        assertEquals(1, result.data.size)
        assertEquals("0xb000000000000000000000000000000000000000", result.data[0].address)
        assertEquals(2L, result.data[0].rank)
        assertEquals("100|0xb000000000000000000000000000000000000000", result.pagination.cursor)

        val andCriteria = querySlot.captured.queryObject["\$and"] as List<*>
        val cursorCriteria = (andCriteria[1] as Document)["\$or"] as List<*>
        val lessThanBalanceCriteria = cursorCriteria[0] as Document
        val equalBalanceCriteria = cursorCriteria[1] as Document
        assertEquals(
            BigInteger("100"),
            (lessThanBalanceCriteria["totalBalance"] as Document)["\$lt"],
        )
        assertEquals(BigInteger("100"), equalBalanceCriteria["totalBalance"])
        assertEquals(
            "0xafffffffffffffffffffffffffffffffffffffff",
            (equalBalanceCriteria["_id"] as Document)["\$gt"],
        )

        val startRankAndCriteria = countQueries.single().queryObject["\$and"] as List<*>
        val startRankPositionCriteria = (startRankAndCriteria[1] as Document)["\$or"] as List<*>
        val startRankEqualBalanceCriteria = startRankPositionCriteria[1] as Document
        assertEquals(BigInteger("100"), startRankEqualBalanceCriteria["totalBalance"])
        assertEquals(
            "0xb000000000000000000000000000000000000000",
            (startRankEqualBalanceCriteria["_id"] as Document)["\$lt"],
        )
    }

    @Test
    fun `getRichlist rejects non integer cursor sort values`() {
        val exception =
            assertThrows(BadRequestException::class.java) {
                service.getRichlist(
                    size = 1,
                    direction = "DESC",
                    cursor = "100.5|0xafffffffffffffffffffffffffffffffffffffff",
                    scope = RichlistScope.ALL,
                )
            }

        assertEquals(
            "Invalid cursor sort value for B3TR richlist: expected integer balance",
            exception.message,
        )
    }

    @Test
    fun `getRichlist ASC uses forward tie breaker for equal balances`() {
        val querySlot = slot<Query>()
        val countQueries = mutableListOf<Query>()
        val bob =
            B3trBalance(
                address = "0xb000000000000000000000000000000000000000",
                blockId = "0xb",
                blockNumber = 2L,
                blockTimestamp = 2000L,
                version = 1,
                vot3Balance = BigInteger("60"),
                b3trBalance = BigInteger("40"),
                totalBalance = BigInteger("100"),
            )
        val carol =
            B3trBalance(
                address = "0xc000000000000000000000000000000000000000",
                blockId = "0xb",
                blockNumber = 2L,
                blockTimestamp = 2000L,
                version = 1,
                vot3Balance = BigInteger("60"),
                b3trBalance = BigInteger("40"),
                totalBalance = BigInteger("100"),
            )
        every { mongoTemplate.find(capture(querySlot), any<Class<*>>(), any<String>()) } returns
            listOf(carol, bob)
        every { mongoTemplate.count(capture(countQueries), any<Class<*>>(), any<String>()) } returns
            1L

        val result =
            service.getRichlist(
                size = 1,
                direction = "ASC",
                cursor = "100|0xb000000000000000000000000000000000000000",
                scope = RichlistScope.ALL,
            )

        assertEquals(1, result.data.size)
        assertEquals("0xc000000000000000000000000000000000000000", result.data[0].address)
        assertEquals("100|0xc000000000000000000000000000000000000000", result.pagination.cursor)

        val andCriteria = querySlot.captured.queryObject["\$and"] as List<*>
        val cursorCriteria = (andCriteria[1] as Document)["\$or"] as List<*>
        val equalBalanceCriteria = cursorCriteria[1] as Document
        assertEquals(BigInteger("100"), equalBalanceCriteria["totalBalance"])
        assertEquals(
            "0xb000000000000000000000000000000000000000",
            (equalBalanceCriteria["_id"] as Document)["\$gt"],
        )

        val startRankAndCriteria = countQueries.single().queryObject["\$and"] as List<*>
        val startRankPositionCriteria = (startRankAndCriteria[1] as Document)["\$or"] as List<*>
        val startRankEqualBalanceCriteria = startRankPositionCriteria[1] as Document
        assertEquals(BigInteger("100"), startRankEqualBalanceCriteria["totalBalance"])
        assertEquals(
            "0xc000000000000000000000000000000000000000",
            (startRankEqualBalanceCriteria["_id"] as Document)["\$lt"],
        )
    }

    @Test
    fun `getRichlist scope VOT3 returns items with vot3Balance`() {
        val doc =
            B3trBalance(
                address = "0xaddr0000000000000000000000000000000001",
                blockId = "0xb",
                blockNumber = 2L,
                blockTimestamp = 2000L,
                version = 1,
                vot3Balance = BigInteger("99"),
                b3trBalance = BigInteger("1"),
                totalBalance = BigInteger("100"),
            )
        every { mongoTemplate.find(any<Query>(), any<Class<*>>(), any<String>()) } returns
            listOf(doc)
        every { mongoTemplate.count(any<Query>(), any<Class<*>>(), any<String>()) } returns 0

        val result = service.getRichlist(size = 20, direction = "DESC", scope = RichlistScope.VOT3)

        assertEquals(1, result.data.size)
        assertEquals(BigInteger("99"), result.data[0].balance)
    }

    @Test
    fun `getRichlist scope B3TR returns items with b3trBalance`() {
        val doc =
            B3trBalance(
                address = "0xaddr0000000000000000000000000000000001",
                blockId = "0xb",
                blockNumber = 2L,
                blockTimestamp = 2000L,
                version = 1,
                vot3Balance = BigInteger("1"),
                b3trBalance = BigInteger("99"),
                totalBalance = BigInteger("100"),
            )
        every { mongoTemplate.find(any<Query>(), any<Class<*>>(), any<String>()) } returns
            listOf(doc)
        every { mongoTemplate.count(any<Query>(), any<Class<*>>(), any<String>()) } returns 0

        val result = service.getRichlist(size = 20, direction = "DESC", scope = RichlistScope.B3TR)

        assertEquals(1, result.data.size)
        assertEquals(BigInteger("99"), result.data[0].balance)
    }
}
