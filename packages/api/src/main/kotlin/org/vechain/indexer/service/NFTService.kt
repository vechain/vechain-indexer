package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.vechain.indexer.model.NFT
import org.vechain.indexer.repos.NFTRepo
import org.vechain.indexer.utils.HexUtil

@Profile("nft-events")
@Service
open class NFTService(private val nftRepo: NFTRepo, private val mongoTemplate: MongoTemplate) {

    open fun findByOwner(owner: String, pageable: Pageable): List<NFT> {
        return nftRepo.findAllByOwner(HexUtil.normalise(owner), pageable)
    }

    open fun findByOwnerAndContractAddress(
        owner: String,
        contractAddress: String,
        pageable: Pageable
    ): List<NFT> {
        return nftRepo.findAllByOwnerAndContractAddress(
            HexUtil.normalise(owner),
            contractAddress,
            pageable
        )
    }

    open fun findContractsByNFTOwner(owner: String): List<String> {
        val query = Query(Criteria.where("owner").`is`(HexUtil.normalise(owner)))
        return mongoTemplate.findDistinct(query, "contractAddress", NFT::class.java, String::class.java)
    }

}
