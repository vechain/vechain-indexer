package org.vechain.indexer.service

import org.bson.Document
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Service
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.repos.NFTRepo
import org.vechain.indexer.utils.HexUtil


@Profile("nft-events")
@Service
open class NFTService(private val nftRepo: NFTRepo, private val mongoTemplate: MongoTemplate) {

    companion object {
        const val TOTAL = "total"
        val OWNER = IndexedNFT::owner.name
        val CONTRACT_ADDRESS = IndexedNFT::contractAddress.name
    }

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
        val matchAggregation = Aggregation.match(Criteria.where(OWNER).`is`(HexUtil.normalise(owner)))
        val groupAggregation = Aggregation.group(CONTRACT_ADDRESS)

        // count distinct contracts
        val countAggregation = Aggregation.newAggregation(
            matchAggregation,
            groupAggregation,
            Aggregation.count().`as`(TOTAL)
        )
        val count = mongoTemplate
            .aggregate(countAggregation, IndexedNFT::class.java, Document::class.java)
            .uniqueMappedResult
        val distinctCount = if (count == null) 0 else count.getInteger(TOTAL)

        // find distinct contracts
        val contractsAggregation = Aggregation.newAggregation(
            matchAggregation,
            groupAggregation,
            Aggregation.sort(pageable.sort),
            Aggregation.skip((pageable.pageNumber * pageable.pageSize).toLong()),
            Aggregation.limit(pageable.pageSize.toLong())
        )
        val distinctContracts = mongoTemplate
            .aggregate(contractsAggregation, IndexedNFT::class.java, Document::class.java)
            .mappedResults
            .map { it["_id"] as String }

        return PageImpl(distinctContracts, pageable, distinctCount.toLong())
    }


}
