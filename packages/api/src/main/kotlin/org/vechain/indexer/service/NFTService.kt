package org.vechain.indexer.service

import org.bson.Document
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.repos.NFTRepo
import org.vechain.indexer.utils.HexUtil


@Profile("nft-events")
@Service
open class NFTService(private val nftRepo: NFTRepo, private val mongoTemplate: MongoTemplate) {

    open fun findByOwner(owner: String, pageable: Pageable): Page<IndexedNFT> {
        return nftRepo.findAllByOwner(HexUtil.normalise(owner), pageable)
    }

    open fun findByOwnerAndContractAddress(
        owner: String,
        contractAddress: String,
        pageable: Pageable
    ): Page<IndexedNFT> {
        return nftRepo.findAllByOwnerAndContractAddress(
            HexUtil.normalise(owner),
            contractAddress,
            pageable
        )
    }

    open fun findContractsByNFTOwner(owner: String, pageable: Pageable): Page<String> {
        val ownerCriteria = Criteria.where("owner").`is`(HexUtil.normalise(owner))
        val query = Query(ownerCriteria).with(pageable)

        // count distinct contracts
        val aggregation = Aggregation.newAggregation(
            Aggregation.match(ownerCriteria),
            Aggregation.group("contractAddress"),
            Aggregation.count().`as`("total")
        )
        val result = mongoTemplate
            .aggregate(aggregation, IndexedNFT::class.java, Document::class.java)
            .uniqueMappedResult
        val count = if (result == null) 0 else result.getInteger("total")

        // find distinct contracts
        val results = mongoTemplate.findDistinct(query, "contractAddress", IndexedNFT::class.java, String::class.java)

        return PageImpl(results, pageable, count.toLong())
    }


}
