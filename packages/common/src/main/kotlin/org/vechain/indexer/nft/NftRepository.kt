package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository

@Profile("nfts")
@Repository
interface NftRepository : BasePagingAndSortingIndexedRepository<IndexedNft, String> {

    fun findByOwner(
        owner: String,
        excludeCollections: List<String>,
        pageable: Pageable,
    ): Slice<IndexedNft>

    fun findByOwnerAndContractAddress(
        owner: String,
        contractAddress: String,
        excludeCollections: List<String>,
        pageable: Pageable,
    ): Slice<IndexedNft>

    fun findByOwnerAndContractAddressAndTokenId(
        owner: String,
        contractAddress: String,
        tokenId: String,
        excludeCollections: List<String>,
        pageable: Pageable,
    ): Slice<IndexedNft>

    fun findContractsByNftOwner(
        owner: String,
        excludeCollections: List<String>,
        pageable: Pageable,
    ): Slice<String>
}
