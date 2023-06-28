package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedFungibleTokenContracts

@Profile("fungible-token-contracts")
@Repository
interface FungibleTokenContractsRepository : BaseIndexedRepository<IndexedFungibleTokenContracts>
