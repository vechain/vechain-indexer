package org.vechain.indexer.b3tr.richlist

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.b3tr.balance.B3trBalance
import org.vechain.indexer.b3tr.balance.repository.B3trBalanceRepository
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
        every { b3trRepository.findById(address) } returns Optional.of(doc)
        var countInvocations = 0
        every { mongoTemplate.count(any<Query>(), any(), any<String>()) } answers
            {
                countInvocations++
                if (countInvocations == 1) 100L else 4L
            }

        val result = service.getAddressRank(address, RichlistScope.ALL)

        assertEquals(address, result.address)
        assertEquals(BigInteger("150"), result.balance)
        assertEquals(5, result.rank)
        assertEquals(100, result.totalHolders)
        assertEquals(5.0, result.topPercentage)
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
