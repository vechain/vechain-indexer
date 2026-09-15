package org.vechain.indexer.safe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.HexUtils.toHex

internal class SafeProxyServiceTest {

    private val factory = "0xffff000000000000000000000000000000000000"
    private val proxyA = "0x1111111111111111111111111111111111111111"
    private val proxyB = "0x2222222222222222222222222222222222222222"
    private val singleton = "0x9999999999999999999999999999999999999999"
    private val txId = "0x" + "cc".repeat(32)

    private val service = SafeProxyService(factory)

    private fun creation(proxy: String, blockNumber: Long = 10L, address: String = factory) =
        buildIndexedEvent(
            blockId = toHex(blockNumber, 64),
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 100L,
            txId = txId,
            address = address,
            eventType = SafeEventUtils.PROXY_CREATION,
            params =
                AbiEventParameters(
                    returnValues = mapOf("proxy" to proxy, "singleton" to singleton)
                ),
        )

    @Test
    fun `the factory's creations become proxies, one per address`() {
        val proxies = service.processEvents(listOf(creation(proxyA), creation(proxyB, 11)))

        assertEquals(listOf(proxyA, proxyB), proxies.map { it.address })
        assertEquals(singleton, proxies[0].singleton)
        assertEquals(10L, proxies[0].createdBlock)
        assertEquals(1000L, proxies[0].createdTimestamp)
        assertEquals(txId, proxies[0].vechainTxId)
    }

    @Test
    fun `a creation emitted by anything but the factory is not a Safe`() {
        val impostor = "0xdead00000000000000000000000000000000dead"

        assertEquals(
            emptyList<SafeProxy>(),
            service.processEvents(listOf(creation(proxyA, address = impostor))),
        )
        assertEquals(emptyList<SafeProxy>(), service.processEvents(emptyList()))
    }
}
