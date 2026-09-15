package org.vechain.indexer.safe

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.safe.SafeEventUtils.PROXY_CREATION
import org.vechain.indexer.safe.SafeEventUtils.addressParam
import org.vechain.indexer.thor.HexUtils

/** Records the factory's deployments; the signature proves nothing, any contract can emit it. */
@Profile("safe")
@Service
open class SafeProxyService(
    @param:Value("\${business-event.substitutions.SAFE_PROXY_FACTORY_CONTRACT}")
    private val factoryAddress: String
) {

    open fun processEvents(events: List<IndexedEvent>): List<SafeProxy> =
        events
            .filter { it.eventType == PROXY_CREATION && isFactory(it.address) }
            .mapNotNull(::toProxy)
            // ProxyCreation fires once per Safe; if it somehow repeats, the deployment stands.
            .distinctBy { it.address }

    private fun isFactory(address: String?): Boolean =
        address != null && address.equals(factoryAddress, ignoreCase = true)

    private fun toProxy(event: IndexedEvent): SafeProxy? {
        val proxy = addressParam(event, "proxy") ?: return null
        val singleton = addressParam(event, "singleton") ?: return null
        return SafeProxy(
            address = proxy,
            singleton = singleton,
            createdBlock = event.blockNumber,
            createdTimestamp = event.blockTimestamp,
            vechainTxId = HexUtils.normalise(event.txId),
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
        )
    }
}
