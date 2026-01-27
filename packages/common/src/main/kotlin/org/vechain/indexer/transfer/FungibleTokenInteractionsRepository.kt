package org.vechain.indexer.transfer

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface FungibleTokenInteractionsRepository : PostgresIndexedRepository {

    fun saveAll(interactions: List<FungibleTokenInteraction>)

    fun findByWalletAddress(
        walletAddress: String,
        pageable: Pageable,
    ): Slice<FungibleTokenInteraction>

    fun findAllByWalletAddressAndContractAddresses(
        walletAddress: String,
        contractAddresses: List<String>,
        pageable: Pageable,
    ): Slice<FungibleTokenInteraction>
}
