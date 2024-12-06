package org.vechain.indexer.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.model.NFTArchive
import org.vechain.indexer.repository.NFTRepository
import org.vechain.indexer.utils.IdUtils

@Profile("nft-events")
@Service
open class NFTService(
    private val nftRepository: NFTRepository,
    mongoTemplate: MongoTemplate,
    @Value("\${indexer.pruner.limit}") private val prunerLimit: Int,
) :
    ArchiveService<IndexedNFT, NFTArchive>(
        mongoTemplate,
        IndexedNFT::class.java,
        NFTArchive::class.java,
        prunerLimit
    ) {

    @Transactional(rollbackFor = [Exception::class])
    override fun update(updated: List<IndexedNFT>, existing: List<IndexedNFT>) {
        if (updated.isNotEmpty()) {
            // Save the documents with the updated version
            nftRepository.saveAll(updated)
        }

        archive(existing)
    }

    open fun getExisting(nftTransfers: List<IndexedTransferEvent>): List<IndexedNFT> {
        return nftRepository.findAllById(nftTransfers.map { IdUtils.buildNftId(it) }).toList()
    }
}
