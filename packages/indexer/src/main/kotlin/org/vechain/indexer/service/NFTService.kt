package org.vechain.indexer.service

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.stereotype.Service
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.repository.ArchiveRepo

@Service
class NFTService(private val archiveRepo: ArchiveRepo) {
    fun save(nfts: List<IndexedNFT>) {
        val archives = nfts.map { Archive(buildHashedId("${it.id}-${it.version}"), it) }
        archiveRepo.saveAll(archives)
    }


    fun getPreviousVersion(nft: IndexedNFT): IndexedNFT {
        val previousVersion = archiveRepo.findById(buildHashedId("${nft.id}-${nft.version - 1}"))
        if (!previousVersion.isPresent) throw Exception("Previous version not found")
        return previousVersion.get().data as IndexedNFT
    }
    
    private fun buildHashedId(plainId: String) = DigestUtils.sha1Hex(plainId)
}