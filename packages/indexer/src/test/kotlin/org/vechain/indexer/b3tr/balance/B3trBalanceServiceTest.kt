package org.vechain.indexer.b3tr.balance

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.b3tr.balance.repository.B3trBalanceRepository
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.utils.BlockDetails

@ExtendWith(MockKExtension::class)
internal class B3trBalanceServiceTest {

    @MockK lateinit var repository: B3trBalanceRepository
    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var inlineVersioningProperties: InlineVersioningProperties

    private val b3trContract = "0xb3tr0000000000000000000000000000000001"
    private val vot3Contract = "0xvot30000000000000000000000000000000001"

    private lateinit var service: B3trBalanceService

    private fun transferEvent(
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        from: String,
        to: String,
        value: String,
        contractAddress: String,
    ) =
        buildIndexedEvent(
            id = "evt-$blockNumber-$from-$to",
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            eventType = "Transfer",
            address = contractAddress,
            params =
                AbiEventParameters(
                    returnValues = mapOf("from" to from, "to" to to, "value" to value)
                ),
        )

    @BeforeEach
    fun setUp() {
        service =
            B3trBalanceService(
                repository = repository,
                mongoTemplate = mongoTemplate,
                inlineVersioningProperties = inlineVersioningProperties,
                b3trContractAddress = b3trContract,
                vot3ContractAddress = vot3Contract,
            )
        every { repository.findById(any<String>()) } returns Optional.empty()
        every { repository.findAllById(any<Iterable<String>>()) } returns emptyList()
    }

    @Test
    fun `processBlock with no Transfer events returns empty`() {
        val blockDetails = BlockDetails("0xblock", 10L, 1000L)
        val otherEvent =
            buildIndexedEvent(
                blockId = "0xblock",
                blockNumber = 10L,
                blockTimestamp = 1000L,
                eventType = "Approval",
                address = b3trContract,
                params = AbiEventParameters(returnValues = emptyMap()),
            )

        val (updated, existing) = service.processBlock(blockDetails, listOf(otherEvent))

        assertEquals(0, updated.size)
        assertEquals(0, existing.size)
    }

    @Test
    fun `processBlock with single VOT3 Transfer creates two records with vot3Balance and totalBalance`() {
        val blockDetails = BlockDetails("0xblock", 10L, 1000L)
        val from = "0xfrom0000000000000000000000000000000001"
        val to = "0xto000000000000000000000000000000000002"
        val events =
            listOf(
                transferEvent(
                    blockDetails.blockId,
                    blockDetails.blockNumber,
                    blockDetails.blockTimestamp,
                    from = from,
                    to = to,
                    value = "100",
                    contractAddress = vot3Contract,
                )
            )

        val (updated, existing) = service.processBlock(blockDetails, events)

        assertEquals(2, updated.size)
        assertEquals(0, existing.size)
        val byAddress = updated.associateBy { it.address }
        assertEquals(BigInteger.ZERO.subtract(BigInteger("100")), byAddress[from]!!.vot3Balance)
        assertEquals(BigInteger.ZERO, byAddress[from]!!.b3trBalance)
        assertEquals(BigInteger.ZERO.subtract(BigInteger("100")), byAddress[from]!!.totalBalance)
        assertEquals(BigInteger("100"), byAddress[to]!!.vot3Balance)
        assertEquals(BigInteger.ZERO, byAddress[to]!!.b3trBalance)
        assertEquals(BigInteger("100"), byAddress[to]!!.totalBalance)
    }

    @Test
    fun `processBlock with single B3TR Transfer updates b3trBalance and totalBalance`() {
        val blockDetails = BlockDetails("0xblock", 10L, 1000L)
        val from = "0xfrom0000000000000000000000000000000001"
        val to = "0xto000000000000000000000000000000000002"
        val events =
            listOf(
                transferEvent(
                    blockDetails.blockId,
                    blockDetails.blockNumber,
                    blockDetails.blockTimestamp,
                    from = from,
                    to = to,
                    value = "50",
                    contractAddress = b3trContract,
                )
            )

        val (updated, existing) = service.processBlock(blockDetails, events)

        assertEquals(2, updated.size)
        val byAddress = updated.associateBy { it.address }
        assertEquals(BigInteger.ZERO, byAddress[from]!!.vot3Balance)
        assertEquals(BigInteger("-50"), byAddress[from]!!.b3trBalance)
        assertEquals(BigInteger("-50"), byAddress[from]!!.totalBalance)
        assertEquals(BigInteger.ZERO, byAddress[to]!!.vot3Balance)
        assertEquals(BigInteger("50"), byAddress[to]!!.b3trBalance)
        assertEquals(BigInteger("50"), byAddress[to]!!.totalBalance)
    }

    @Test
    fun `processBlock with both VOT3 and B3TR transfers in same block aggregates correctly`() {
        val blockDetails = BlockDetails("0xblock", 10L, 1000L)
        val alice = "0xalice0000000000000000000000000000000001"
        val bob = "0xbob000000000000000000000000000000000002"
        val events =
            listOf(
                transferEvent(
                    blockDetails.blockId,
                    blockDetails.blockNumber,
                    blockDetails.blockTimestamp,
                    from = alice,
                    to = bob,
                    value = "30",
                    contractAddress = vot3Contract,
                ),
                transferEvent(
                    blockDetails.blockId,
                    blockDetails.blockNumber,
                    blockDetails.blockTimestamp,
                    from = bob,
                    to = alice,
                    value = "10",
                    contractAddress = b3trContract,
                ),
            )

        val (updated, existing) = service.processBlock(blockDetails, events)

        assertEquals(2, updated.size)
        val byAddress = updated.associateBy { it.address }
        assertEquals(BigInteger("-30"), byAddress[alice]!!.vot3Balance)
        assertEquals(BigInteger("10"), byAddress[alice]!!.b3trBalance)
        assertEquals(BigInteger("-20"), byAddress[alice]!!.totalBalance)
        assertEquals(BigInteger("30"), byAddress[bob]!!.vot3Balance)
        assertEquals(BigInteger("-10"), byAddress[bob]!!.b3trBalance)
        assertEquals(BigInteger("20"), byAddress[bob]!!.totalBalance)
    }

    @Test
    fun `processBlock ignores Transfer from other contract`() {
        val blockDetails = BlockDetails("0xblock", 10L, 1000L)
        val otherContract = "0xother0000000000000000000000000000000001"
        val from = "0xfrom0000000000000000000000000000000001"
        val to = "0xto000000000000000000000000000000000002"
        val events =
            listOf(
                transferEvent(
                    blockDetails.blockId,
                    blockDetails.blockNumber,
                    blockDetails.blockTimestamp,
                    from = from,
                    to = to,
                    value = "100",
                    contractAddress = otherContract,
                )
            )

        val (updated, existing) = service.processBlock(blockDetails, events)

        assertEquals(0, updated.size)
        assertEquals(0, existing.size)
    }

    @Test
    fun `processBlock with existing address loads from repository and updates`() {
        val blockDetails = BlockDetails("0xblock", 10L, 1000L)
        val from = "0xfrom0000000000000000000000000000000001"
        val to = "0xto000000000000000000000000000000000002"
        every { repository.findById(from) } returns
            Optional.of(
                B3trBalance(
                    address = from,
                    blockId = "0xprev",
                    blockNumber = 9L,
                    blockTimestamp = 900L,
                    version = 1,
                    vot3Balance = BigInteger("200"),
                    b3trBalance = BigInteger("50"),
                    totalBalance = BigInteger("250"),
                )
            )
        every { repository.findById(to) } returns Optional.empty()
        val events =
            listOf(
                transferEvent(
                    blockDetails.blockId,
                    blockDetails.blockNumber,
                    blockDetails.blockTimestamp,
                    from = from,
                    to = to,
                    value = "25",
                    contractAddress = vot3Contract,
                )
            )

        val (updated, existing) = service.processBlock(blockDetails, events)

        assertEquals(2, updated.size)
        val byAddress = updated.associateBy { it.address }
        assertEquals(BigInteger("175"), byAddress[from]!!.vot3Balance)
        assertEquals(BigInteger("50"), byAddress[from]!!.b3trBalance)
        assertEquals(BigInteger("225"), byAddress[from]!!.totalBalance)
        assertEquals(BigInteger("25"), byAddress[to]!!.vot3Balance)
        assertEquals(BigInteger.ZERO, byAddress[to]!!.b3trBalance)
        assertEquals(BigInteger("25"), byAddress[to]!!.totalBalance)
    }

    @Test
    fun `processBlock skips zero-address and does not create record for it`() {
        val blockDetails = BlockDetails("0xblock", 10L, 1000L)
        val zero = "0x0000000000000000000000000000000000000000"
        val to = "0xto000000000000000000000000000000000002"
        val events =
            listOf(
                transferEvent(
                    blockDetails.blockId,
                    blockDetails.blockNumber,
                    blockDetails.blockTimestamp,
                    from = zero,
                    to = to,
                    value = "100",
                    contractAddress = vot3Contract,
                )
            )

        val (updated, existing) = service.processBlock(blockDetails, events)

        assertEquals(1, updated.size)
        assertEquals(to, updated.single().address)
        assertEquals(BigInteger("100"), updated.single().vot3Balance)
    }

    @Test
    fun `processBlock B3TR transfer to VOT3 contract skips VOT3 contract address`() {
        val blockDetails = BlockDetails("0xblock", 10L, 1000L)
        val from = "0xfrom0000000000000000000000000000000001"
        val events =
            listOf(
                transferEvent(
                    blockDetails.blockId,
                    blockDetails.blockNumber,
                    blockDetails.blockTimestamp,
                    from = from,
                    to = vot3Contract,
                    value = "100",
                    contractAddress = b3trContract,
                )
            )

        val (updated, existing) = service.processBlock(blockDetails, events)

        assertEquals(1, updated.size)
        assertEquals(from, updated.single().address)
        assertEquals(BigInteger("-100"), updated.single().b3trBalance)
        assertEquals(BigInteger("-100"), updated.single().totalBalance)
    }
}
