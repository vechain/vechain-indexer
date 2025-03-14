package org.vechain.indexer.service

import org.jetbrains.annotations.TestOnly
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.vechain.indexer.model.*

@Profile("e2e")
@Service
open class E2EService(private val mongoTemplate: MongoTemplate) {

    @TestOnly
    open fun getNftArchives(): List<NFTArchive> {
        return mongoTemplate.findAll(NFTArchive::class.java)
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
        return mongoTemplate.findAll(IndexedNFT::class.java)
    }
}
