package org.vechain.indexer.service

import org.springframework.stereotype.Service
import org.vechain.indexer.model.NFT
import org.vechain.indexer.repos.NFTRepo
import org.vechain.indexer.utils.HexUtil

@Service
open class NFTService(private val nftRepo: NFTRepo) {

    open fun findByOwner(owner: String): List<NFT> {
        return nftRepo.findAllByOwner(HexUtil.normalise(owner)).toList()
    }

}