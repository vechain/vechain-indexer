package org.vechain.indexer.safe

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.safe.repository.SafeProxyRepository
import org.vechain.indexer.safe.repository.SafeTxProposalRepository
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsInt
import org.vechain.indexer.utils.ParamUtils.getAsString

/**
 * Aggregates the three SafeEmitter events into one document per `(safe, txHash)`. The Safe address
 * comes from the indexed `safe` event parameter; `event.address` is the emitter contract.
 */
@Profile("safe")
@Service
open class SafeTxProposalService(
    private val repository: SafeTxProposalRepository,
    private val safeProxyRepository: SafeProxyRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {

    companion object {
        const val SAFE_TX_PROPOSED = "SafeTxProposed"
        const val SAFE_TX_HASH_FIELDS = "SafeTxHashFields"
        const val SAFE_BATCH_TX_PROPOSED = "SafeBatchTxProposed"

        private val SUPPORTED_EVENTS =
            setOf(SAFE_TX_PROPOSED, SAFE_TX_HASH_FIELDS, SAFE_BATCH_TX_PROPOSED)
    }

    open fun processBlock(
        events: List<IndexedEvent>
    ): Pair<List<SafeTxProposal>, List<SafeTxProposal>> {
        val proposalEvents = events.filter { it.eventType in SUPPORTED_EVENTS }
        if (proposalEvents.isEmpty()) {
            return emptyList<SafeTxProposal>() to emptyList()
        }

        // Defence-in-depth: SafeEmitter validates the caller is a Safe owner via `isOwner`, but
        // `isOwner` is just an arbitrary view function — a non-Safe contract could fake it. Drop
        // proposals whose indexed `safe` param isn't a registered Safe.
        val verifiedEvents = filterByKnownSafes(proposalEvents)
        if (verifiedEvents.isEmpty()) {
            return emptyList<SafeTxProposal>() to emptyList()
        }

        val candidateIds = mutableSetOf<String>()
        verifiedEvents.forEach { event ->
            val id = buildIdOrNull(event) ?: return@forEach
            candidateIds.add(id)
        }
        val preloaded =
            if (candidateIds.isNotEmpty()) {
                repository.findAllById(candidateIds).associateBy { it.getDocumentId() }
            } else {
                emptyMap()
            }

        val accumulator =
            VersionedDocumentAccumulator<SafeTxProposal>(
                findById = { id -> preloaded[id] ?: repository.findByIdOrNull(id) },
                initialVersion = 1,
            )

        groupByBlock(verifiedEvents).forEach { (blockDetails, blockEvents) ->
            accumulator.startBlock()
            blockEvents.forEach { event -> applyEvent(event, blockDetails, accumulator) }
        }

        return accumulator.results()
    }

    private fun filterByKnownSafes(events: List<IndexedEvent>): List<IndexedEvent> {
        val candidateSafes =
            events
                .mapNotNull { event ->
                    event.params.getAsString("safe")?.let { HexUtils.normalise(it) }
                }
                .toSet()
        if (candidateSafes.isEmpty()) return emptyList()
        val knownSafes = safeProxyRepository.findAllById(candidateSafes).map { it.id }.toSet()
        return events.filter { event ->
            event.params.getAsString("safe")?.let { HexUtils.normalise(it) in knownSafes } == true
        }
    }

    private fun applyEvent(
        event: IndexedEvent,
        blockDetails: BlockDetails,
        accumulator: VersionedDocumentAccumulator<SafeTxProposal>,
    ) {
        val safe = event.params.getAsString("safe")?.let { HexUtils.normalise(it) } ?: return
        val txHash = event.params.getAsString("txHash")?.let { HexUtils.normalise(it) } ?: return
        val recordId = SafeTxProposal.buildId(safe, txHash)
        val (existing, nextVersion) = accumulator.resolve(recordId)

        val base =
            existing?.copy(
                version = nextVersion,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
            )
                ?: SafeTxProposal(
                    id = recordId,
                    safe = safe,
                    txHash = txHash,
                    blockId = blockDetails.blockId,
                    blockNumber = blockDetails.blockNumber,
                    blockTimestamp = blockDetails.blockTimestamp,
                    version = nextVersion,
                )

        when (event.eventType) {
            SAFE_TX_PROPOSED -> applyProposed(base, event, blockDetails)
            SAFE_TX_HASH_FIELDS -> applyHashFields(base, event)
            SAFE_BATCH_TX_PROPOSED -> applyBatch(base, event)
        }
        accumulator.put(recordId, existing, base)
    }

    private fun applyProposed(
        base: SafeTxProposal,
        event: IndexedEvent,
        blockDetails: BlockDetails,
    ) {
        base.proposer = event.params.getAsString("proposer")?.let { HexUtils.normalise(it) }
        base.proposedBlock = blockDetails.blockNumber
        base.proposedTimestamp = blockDetails.blockTimestamp
        base.proposedVechainTxId = event.txId
        base.to = event.params.getAsString("to")?.let { HexUtils.normalise(it) }
        base.value = event.params.getAsBigInteger("value")
        base.data = event.params.getAsString("data")
        base.operation = event.params.getAsInt("operation")
        base.nonce = event.params.getAsBigInteger("nonce")
        base.description =
            event.params.getAsString("description")?.take(SafeTxProposal.DESCRIPTION_MAX_LENGTH)
        base.envelopeRecorded = true
    }

    private fun applyHashFields(base: SafeTxProposal, event: IndexedEvent) {
        base.safeTxGas = event.params.getAsBigInteger("safeTxGas")
        base.baseGas = event.params.getAsBigInteger("baseGas")
        base.gasPrice = event.params.getAsBigInteger("gasPrice")
        base.gasToken = event.params.getAsString("gasToken")?.let { HexUtils.normalise(it) }
        base.refundReceiver =
            event.params.getAsString("refundReceiver")?.let { HexUtils.normalise(it) }
        base.hashFieldsRecorded = true
    }

    private fun applyBatch(base: SafeTxProposal, event: IndexedEvent) {
        val targets = event.params.params["targets"] as? List<*> ?: return
        val values = event.params.params["values"] as? List<*> ?: return
        val datas = event.params.params["datas"] as? List<*> ?: return
        val operations = event.params.params["operations"] as? List<*> ?: return
        val labels = event.params.params["labels"] as? List<*> ?: return
        val len = targets.size
        if (
            values.size != len || datas.size != len || operations.size != len || labels.size != len
        ) {
            return
        }
        base.subcalls =
            (0 until len).map { i ->
                SafeSubcall(
                    target = HexUtils.normalise(targets[i].toString()),
                    value = toBigInteger(values[i]),
                    data = datas[i].toString(),
                    operation = toInt(operations[i]),
                    label = labels[i].toString(),
                )
            }
    }

    private fun toBigInteger(v: Any?): BigInteger =
        when (v) {
            is BigInteger -> v
            is Number -> BigInteger.valueOf(v.toLong())
            is String -> v.toBigIntegerOrNull() ?: BigInteger.ZERO
            else -> BigInteger.ZERO
        }

    private fun toInt(v: Any?): Int =
        when (v) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: 0
            else -> 0
        }

    private fun buildIdOrNull(event: IndexedEvent): String? {
        val safe = event.params.getAsString("safe")?.let { HexUtils.normalise(it) } ?: return null
        val txHash =
            event.params.getAsString("txHash")?.let { HexUtils.normalise(it) } ?: return null
        return SafeTxProposal.buildId(safe, txHash)
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<SafeTxProposal>, existing: List<SafeTxProposal>) {
        saveVersionedDocuments(
            updated = updated,
            existing = existing,
            mongoTemplate = mongoTemplate,
            blockWindow = inlineVersioningProperties.blockWindow,
            maxVersions = inlineVersioningProperties.maxVersions,
        )
    }
}
