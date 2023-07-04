package org.vechain.indexer.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.repository.NFTRepository
import org.vechain.indexer.utils.IdUtils

@Service
open class NFTService(
    private val nftRepository: NFTRepository,
    private val archiveService: ArchiveService,
) {

    @Transactional(rollbackFor = [Exception::class])
    open fun save(current: List<IndexedNFT>, archived: List<IndexedNFT>) {
        if (archived.isNotEmpty()) archiveService.saveAll(archived)
        if (current.isNotEmpty()) nftRepository.saveAll(current)
    }

    open fun getExisting(nftTransfers: List<IndexedTransferEvent>): List<IndexedNFT> {
        return nftRepository
            .findAllById(nftTransfers.map { IdUtils.buildHashedId("${it.tokenAddress}-${it.id}") })
            .toList()
    }
}
