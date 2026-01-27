package org.vechain.indexer.nft

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface NftRepository : PostgresIndexedRepository {

    fun saveAllVersioned(updated: List<IndexedNft>, existing: List<IndexedNft>)

    fun findAllById(ids: List<String>): List<IndexedNft>

    fun findByOwner(
        owner: String,
        excludeCollections: List<String>,
        pageable: Pageable,
    ): Slice<IndexedNft>

    fun findByOwnerAndContractAddress(
        owner: String,
        contractAddress: String,
        pageable: Pageable,
    ): Slice<IndexedNft>

    fun findByOwnerAndContractAddressAndTokenId(
        owner: String,
        contractAddress: String,
        tokenId: String,
        pageable: Pageable,
    ): Slice<IndexedNft>

    fun findContractsByNftOwner(
        owner: String,
        excludeCollections: List<String>,
        pageable: Pageable,
    ): Slice<String>

    fun blacklist(contractAddresses: List<String>)

    fun whitelist(contractAddresses: List<String>)
}
