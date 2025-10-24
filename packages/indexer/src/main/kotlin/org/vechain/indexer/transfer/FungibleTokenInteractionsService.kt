package org.vechain.indexer.transfer

import org.bson.Document
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS

@Service
@Profile("transfers")
open class FungibleTokenInteractionsService(private val mongoTemplate: MongoTemplate) {
    open fun processEvents(events: List<IndexedEvent>): List<FungibleTokenInteraction> {
        return events
            .filter { event ->
                require(event.eventType == "Transfer") {
                    "Event type must be 'Transfer', found '${event.eventType}'"
                }
                event.address != null &&
                    event.address != VTHO_CONTRACT_ADDRESS &&
                    event.params.params["from"] is String &&
                    event.params.params["to"] is String
            }
            .flatMap { event ->
                val from = event.params.params["from"] as String
                val to = event.params.params["to"] as String
                listOf(
                    FungibleTokenInteraction(
                        contractAddress = event.address!!,
                        blockId = event.blockId,
                        blockNumber = event.blockNumber,
                        blockTimestamp = event.blockTimestamp,
                        walletAddress = from,
                    ),
                    FungibleTokenInteraction(
                        contractAddress = event.address!!,
                        blockId = event.blockId,
                        blockNumber = event.blockNumber,
                        blockTimestamp = event.blockTimestamp,
                        walletAddress = to,
                    ),
                )
            }
            .distinctBy { it.contractAddress to it.walletAddress }
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(records: List<FungibleTokenInteraction>) {
        if (records.isEmpty()) return

        val ops =
            mongoTemplate.bulkOps(
                BulkOperations.BulkMode.UNORDERED,
                FungibleTokenInteraction::class.java,
            )

        records.forEach { record ->
            // Serialize the record exactly as Spring would (includes _id and _class if enabled)
            val doc = Document().also { mongoTemplate.converter.write(record, it) }

            // Query by the serialized _id
            val query = Query(Criteria.where("_id").`is`(doc.get("_id")))

            // Build an Update that sets every field only on insert
            val update = Update()
            doc.forEach { (k, v) -> update.setOnInsert(k, v) }

            ops.upsert(query, update)
        }

        ops.execute()
    }
}
