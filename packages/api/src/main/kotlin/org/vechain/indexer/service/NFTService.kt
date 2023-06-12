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
import org.vechain.indexer.repository.CountRepository
import org.vechain.indexer.repository.NFTRepo
import org.vechain.indexer.utils.HexUtils


@Profile("nft-events")
@Service
open class NFTService(
    private val nftRepo: NFTRepo,
    private val countRepository: CountRepository,
    private val mongoTemplate: MongoTemplate
) {

    companion object {
        val NFTS_COLLECTION = IndexedNFT::class.java
        val OWNER = IndexedNFT::owner.name
        val CONTRACT_ADDRESS = IndexedNFT::contractAddress.name
    }

    open fun findByOwner(owner: String, pageable: Pageable): Page<IndexedNFT> {
        val slice = nftRepo.findAllByOwner(HexUtils.normalise(owner), pageable)

        val count =
            countRepository.getCount(
                NFTS_COLLECTION,
                Aggregation.match(Criteria.where(OWNER).`is`(HexUtils.normalise(owner)))
            )

        return PageImpl(slice.content, pageable, count)
    }

    open fun findByOwnerAndContractAddress(
        owner: String,
        contractAddress: String,
        pageable: Pageable
    ): Page<IndexedNFT> {
        val slice = nftRepo.findAllByOwnerAndContractAddress(HexUtils.normalise(owner), contractAddress, pageable)

        val count = countRepository.getCount(
            NFTS_COLLECTION,
            Aggregation.match(
                Criteria.where(OWNER).`is`(HexUtils.normalise(owner)).and(CONTRACT_ADDRESS).`is`(contractAddress)
            )
        )

        return PageImpl(slice.content, pageable, count)
    }

    open fun findContractsByNFTOwner(owner: String, pageable: Pageable): Page<String> {
        val matchAggregation = Aggregation.match(Criteria.where(OWNER).`is`(HexUtils.normalise(owner)))
        val groupAggregation = Aggregation.group(CONTRACT_ADDRESS)

        // count distinct contracts
        val distinctCount = countRepository.getCount(NFTS_COLLECTION, matchAggregation, groupAggregation)

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

        return PageImpl(distinctContracts, pageable, distinctCount)
    }

}
