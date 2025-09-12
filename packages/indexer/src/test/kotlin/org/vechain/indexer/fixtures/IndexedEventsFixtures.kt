package org.vechain.indexer.fixtures

import com.fasterxml.jackson.core.type.TypeReference
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.model.generic.RawEvent
import org.vechain.indexer.utils.JsonUtils

object IndexedEventsFixtures {
    private val objectMapper = JsonUtils.mapper

    val INDEXED_EVENTS_TRANSFERS =
        loadEventsFromFile("indexed-events/indexed_events_transfers.json")

    val INDEXED_EVENTS_BLACKLIST =
        loadEventsFromFile("indexed-events/indexed_events_blacklist.json")

    val INDEXED_EVENTS_WHITELIST =
        loadEventsFromFile("indexed-events/indexed_events_whitelist.json")

    val INDEXED_EVENTS_BLACKLIST_DUPLICATE =
        loadEventsFromFile("indexed-events/indexed_events_blacklist_duplicate.json")

    val INDEXED_EVENTS_NFT_TRANSFER =
        loadEventsFromFile("indexed-events/indexed_events_nft_transfer.json")

    val INDEXED_EVENTS_NFT_TRANSFER_MISSING_TO_PARAM =
        loadEventsFromFile("indexed-events/indexed_events_nft_transfer_missing_to_param.json")

    val INDEXED_EVENTS_NFT_TRANSFER_MISSING_TOKEN_ID_PARAM =
        loadEventsFromFile("indexed-events/indexed_events_nft_transfer_missing_token_id_param.json")

    val INDEXED_EVENTS_NFT_TRANSFER_DUPLICATE =
        loadEventsFromFile("indexed-events/indexed_events_nft_transfer_duplicate.json")

    val INDEXED_EVENTS_NFT_MINT = loadEventsFromFile("indexed-events/indexed_events_nft_mint.json")

    private fun loadEventsFromFile(name: String): List<IndexedEvent> {
        val resource =
            IndexedEventsFixtures::class.java.classLoader.getResource(name)
                ?: throw IllegalStateException("Resource not found: $name")

        return resource.openStream().use { inputStream ->
            objectMapper.readValue(inputStream, object : TypeReference<List<IndexedEvent>>() {})
        }
    }

    /** Utility fixture for building IndexedEvent instances with customizable parameters. */
    fun buildIndexedEvent(
        id: String = "event-id",
        blockId: String = "block-id",
        blockNumber: Long = 1L,
        blockTimestamp: Long = 2L,
        txId: String = "tx-id",
        origin: String = "origin",
        paid: String? = null,
        gasUsed: Long? = null,
        gasPayer: String? = null,
        raw: RawEvent? = null,
        params: AbiEventParameters = AbiEventParameters(),
        address: String? = null,
        eventType: String = "MockEvent",
        clauseIndex: Long = 0,
        signature: String = "sig",
    ): IndexedEvent =
        IndexedEvent(
            id = id,
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            txId = txId,
            origin = origin,
            paid = paid,
            gasUsed = gasUsed,
            gasPayer = gasPayer,
            raw = raw,
            params = params,
            address = address,
            eventType = eventType,
            clauseIndex = clauseIndex,
            signature = signature,
        )
}
