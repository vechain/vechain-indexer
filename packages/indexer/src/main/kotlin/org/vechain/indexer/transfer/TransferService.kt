package org.vechain.indexer.transfer

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS
import org.vechain.indexer.utils.EventUtils
import org.vechain.indexer.utils.ParamUtils.getAsString

@Service
@Profile("transfers")
open class TransferService(private val repository: TransferWriteRepository) {

    /** One entry's rows: its transfers and the fungible contracts they introduce to a wallet. */
    data class Update(
        val transfers: List<IndexedTransferEvent>,
        val interactions: List<FungibleTokenInteraction>,
    )

    open fun processEvents(events: List<IndexedEvent>): Update {
        val transferEvents = mutableListOf<IndexedTransferEvent>()
        val nextTransferIndexByBlock = mutableMapOf<Long, Long>()

        fun nextTransferIndex(blockNumber: Long): Long {
            val index = nextTransferIndexByBlock.getOrDefault(blockNumber, 0L)
            nextTransferIndexByBlock[blockNumber] = index + 1
            return index
        }

        events.forEach { event ->
            val eventName = EventUtils.determineTransferType(event.params) ?: return@forEach

            if (event.params.getEventType() == "TransferBatch") {
                transferEvents.addAll(
                    processBatchTransferEvents(event) { nextTransferIndex(event.blockNumber) }
                )
            } else {
                transferEvents.add(
                    createIndexedTransferEvent(
                        event,
                        eventName,
                        nextTransferIndex(event.blockNumber),
                    )
                )
            }
        }
        return Update(transferEvents, interactions(transferEvents))
    }

    // VTHO is on every wallet, so it is not recorded; a mint or burn introduces only the other
    // side.
    private fun interactions(
        transfers: List<IndexedTransferEvent>
    ): List<FungibleTokenInteraction> =
        transfers
            .filter {
                it.eventType == TransferEventType.FUNGIBLE_TOKEN &&
                    it.tokenAddress != null &&
                    it.tokenAddress != VTHO_CONTRACT_ADDRESS
            }
            .flatMap { t ->
                listOf(t.from, t.to)
                    .filter { it != Address.ZERO_ADDRESS }
                    .map { wallet ->
                        FungibleTokenInteraction(
                            contractAddress = t.tokenAddress!!,
                            blockId = t.blockId,
                            blockNumber = t.blockNumber,
                            blockTimestamp = t.blockTimestamp,
                            walletAddress = wallet,
                        )
                    }
            }
            .distinctBy { it.contractAddress to it.walletAddress }

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(update: Update) = repository.save(update.transfers, update.interactions)

    private fun processBatchTransferEvents(
        event: IndexedEvent,
        nextTransferIndex: () -> Long,
    ): List<IndexedTransferEvent> {
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
                    transferIndex = nextTransferIndex(),
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
        transferIndex: Long,
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
            transferIndex = transferIndex,
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
