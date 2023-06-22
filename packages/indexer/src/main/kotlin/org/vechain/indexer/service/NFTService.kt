package org.vechain.indexer.service

import org.springframework.stereotype.Service
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.repository.ArchiveRepo
import org.vechain.indexer.utils.IdUtils

@Service
class NFTService(private val archiveRepo: ArchiveRepo) {
    fun save(nfts: List<IndexedNFT>) {
        val archives = nfts.map { Archive(IdUtils.buildHashedId("${it.id}-${it.version}"), it) }
        archiveRepo.saveAll(archives)
    }


    fun getPreviousVersion(nft: IndexedNFT): IndexedNFT {
        val previousVersion = archiveRepo.findById(IdUtils.buildHashedId("${nft.id}-${nft.version - 1}"))
        if (!previousVersion.isPresent) throw Exception("Previous version not found")
        return previousVersion.get().data as IndexedNFT
    }

}