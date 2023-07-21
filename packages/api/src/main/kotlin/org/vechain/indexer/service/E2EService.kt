package org.vechain.indexer.service

import org.jetbrains.annotations.TestOnly
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.model.TransferEventType

@Profile("e2e")
@Service
open class E2EService(private val mongoTemplate: MongoTemplate) {

    @TestOnly
    open fun getNftArchives(): List<Archive<IndexedNFT>> {
        val query = Query()
        val criteria = Criteria.where("data._class").`is`("org.vechain.indexer.model.IndexedNFT")
        query.addCriteria(criteria)

        val results = mongoTemplate.find(query, Archive::class.java)
        return results as List<Archive<IndexedNFT>>
    }

    @TestOnly
    open fun getNftTransfers(): List<IndexedTransferEvent> {
        val query = Query()
        val criteria =
            Criteria.where(IndexedTransferEvent::eventType.name).`is`(TransferEventType.NFT)
        query.addCriteria(criteria)

        return mongoTemplate.find(query, IndexedTransferEvent::class.java)
    }

    @TestOnly
    open fun getNfts(): List<IndexedNFT> {
        return mongoTemplate.find(Query(), IndexedNFT::class.java)
    }
}
