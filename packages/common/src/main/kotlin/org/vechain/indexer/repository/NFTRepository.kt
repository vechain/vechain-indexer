package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedNFT

@Profile("nft-events")
@Repository
interface NFTRepository : BaseIndexedRepository<IndexedNFT> {

    fun findByOwner(owner: String, pageable: Pageable): Slice<IndexedNFT>

    fun findByOwnerAndContractAddress(
        owner: String,
        contractAddress: String,
        pageable: Pageable
    ): Slice<IndexedNFT>

    fun findByOwnerAndContractAddressAndTokenId(
        owner: String,
        contractAddress: String,
        tokenId: String,
        pageable: Pageable
    ): Slice<IndexedNFT>

    fun findContractsByNFTOwner(owner: String, pageable: Pageable): Slice<String>
}
