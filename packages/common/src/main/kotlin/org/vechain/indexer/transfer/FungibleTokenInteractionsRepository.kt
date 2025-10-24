package org.vechain.indexer.transfer

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository

@Profile("transfers")
@Repository
interface FungibleTokenInteractionsRepository :
    BasePagingAndSortingIndexedRepository<FungibleTokenInteraction, String> {

    @Query("{ 'walletAddress': ?0 }", fields = "{ 'contractAddress': 1 }")
    fun findByWalletAddress(walletAddress: String, pageable: Pageable): Slice<String>

    @Query(
        "{ 'walletAddress': ?0, 'contractAddress': { '\$in': ?1 } }",
        fields = "{ 'contractAddress': 1 }",
    )
    fun findAllByWalletAddressAndContractAddresses(
        walletAddress: String,
        contractAddresses: List<String>,
        pageable: Pageable,
    ): Slice<String>
}
