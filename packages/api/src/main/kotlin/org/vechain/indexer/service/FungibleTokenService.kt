package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.repository.FungibleTokenContractsRepository

@Service
@Profile("fungible-token-contracts")
class FungibleTokenService(private val fungibleTokensRepository: FungibleTokenContractsRepository) {

    fun getContractsForOwner(owner: String): MutableSet<String> {
        val fungibleContracts = fungibleTokensRepository.findByIdOrNull(owner)

        return fungibleContracts?.tokenAddresses ?: mutableSetOf()
    }
}
