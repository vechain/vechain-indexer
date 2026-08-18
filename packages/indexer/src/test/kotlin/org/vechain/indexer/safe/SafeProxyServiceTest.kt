package org.vechain.indexer.safe

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.safe.repository.SafeProxyRepository

@ExtendWith(MockKExtension::class)
internal class SafeProxyServiceTest {

    @MockK lateinit var repository: SafeProxyRepository
    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var inlineVersioningProperties: InlineVersioningProperties

    private val factory = "0xFFFF000000000000000000000000000000000000"
    private val proxyA = "0x1111111111111111111111111111111111111111"
    private val proxyB = "0x2222222222222222222222222222222222222222"
    private val singleton = "0x9999999999999999999999999999999999999999"

    private lateinit var service: SafeProxyService

    @BeforeEach
    fun setUp() {
        service = SafeProxyService(repository, mongoTemplate, inlineVersioningProperties)
        every { repository.findById(any<String>()) } returns Optional.empty()
        every { repository.findAllById(any<Iterable<String>>()) } returns emptyList()
    }

    private fun proxyCreationEvent(
        proxy: String,
        blockNumber: Long = 10L,
        txId: String = "0xchaintx",
    ) =
        buildIndexedEvent(
            blockId = "0xblock",
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 100L,
            txId = txId,
            address = factory,
            eventType = SafeProxyService.PROXY_CREATION,
            params =
                AbiEventParameters(
                    returnValues = mapOf("proxy" to proxy, "singleton" to singleton)
                ),
        )

    @Test
    fun `processBlock with no relevant events returns empty`() {
        val (updated, existing) = service.processBlock(emptyList())
        assertEquals(0, updated.size)
        assertEquals(0, existing.size)
    }

    @Test
    fun `ProxyCreation creates a SafeProxy doc keyed by the proxy address`() {
        val (updated, existing) = service.processBlock(listOf(proxyCreationEvent(proxyA)))

        assertEquals(1, updated.size)
        assertEquals(0, existing.size)
        val doc = updated.single()
        assertEquals(proxyA.lowercase(), doc.id)
        assertEquals(singleton.lowercase(), doc.singleton)
        assertEquals(10L, doc.createdBlock)
        assertEquals(1, doc.version)
    }

    @Test
    fun `Multiple ProxyCreation events in a block produce one doc per proxy`() {
        val (updated, _) =
            service.processBlock(listOf(proxyCreationEvent(proxyA), proxyCreationEvent(proxyB)))

        val ids = updated.map { it.id }.toSet()
        assertEquals(setOf(proxyA.lowercase(), proxyB.lowercase()), ids)
    }
}
