package org.vechain.indexer.transfer

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.EventUtils
import org.vechain.indexer.utils.ParamUtils.getAsString

@Service
@Profile("transfers", "transfers-only")
open class TransferService(private val repository: TransferEventRepository) {
    open fun processEvents(events: List<IndexedEvent>): List<IndexedTransferEvent> {
        val transferEvents = mutableListOf<IndexedTransferEvent>()
        events.forEach { event ->
            val eventName = EventUtils.determineTransferType(event.params) ?: return@forEach

            if (event.params.getEventType() == "TransferBatch") {
                transferEvents.addAll(processBatchTransferEvents(event))
            } else {
                transferEvents.add(createIndexedTransferEvent(event, eventName))
            }
        }
        return transferEvents
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(records: List<IndexedTransferEvent>) {
        repository.saveAll(records)
    }

    private fun processBatchTransferEvents(event: IndexedEvent): List<IndexedTransferEvent> {
        val transferEvents = mutableListOf<IndexedTransferEvent>()

        val tokenIds = event.params.getReturnValues()["ids"] as? List<*> ?: emptyList<Any>()
        val values = event.params.getReturnValues()["values"] as? List<*> ?: emptyList<Any>()

        for (i in tokenIds.indices) {
            transferEvents.add(
                IndexedTransferEvent(
                    id = DigestUtils.sha1Hex("${event.id}-$i"),
                    blockId = event.blockId,
                    blockNumber = event.blockNumber,
                    blockTimestamp = event.blockTimestamp,
                    txId = event.txId,
                    from = event.params.getAsString("from")!!,
                    to = event.params.getAsString("to")!!,
                    value = values.getOrNull(i)?.toString()!!,
                    topics = event.raw!!.topics,
                    tokenAddress = event.address,
                    tokenId = tokenIds.getOrNull(i)?.toString(),
                    eventType = TransferEventType.SEMI_FUNGIBLE_TOKEN,
                )
            )
        }
        return transferEvents
    }

    private fun createIndexedTransferEvent(
        event: IndexedEvent,
        transferEventType: TransferEventType,
    ): IndexedTransferEvent {
        val params = event.params

        val tokenId =
            when (transferEventType) {
                TransferEventType.SEMI_FUNGIBLE_TOKEN -> params.getAsString("id")
                else -> params.getAsString("tokenId")
            }

        val value =
            when (transferEventType) {
                TransferEventType.VET -> params.getAsString("amount")!!
                else -> params.getAsString("value") ?: "1"
            }

        return IndexedTransferEvent(
            id = DigestUtils.sha1Hex(event.id),
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            txId = event.txId,
            from = params.getAsString("from")!!,
            to = params.getAsString("to")!!,
            value = value,
            topics = event.raw?.topics.orEmpty(),
            tokenAddress = event.address,
            tokenId = tokenId,
            eventType = transferEventType,
        )
    }
}
