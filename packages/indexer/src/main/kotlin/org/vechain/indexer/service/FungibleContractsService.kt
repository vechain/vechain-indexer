package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.model.IndexedFungibleTokenContracts
import org.vechain.indexer.repository.FungibleTokenContractsRepository

@Service
@Profile("fungible-token-contracts")
open class FungibleContractsService(
    private val fungibleTokenContractsRepository: FungibleTokenContractsRepository,
    private val archiveService: ArchiveService
) {

    @Transactional(rollbackFor = [Exception::class])
    open fun saveAll(
        existing: List<IndexedFungibleTokenContracts>,
        archived: List<IndexedFungibleTokenContracts>
    ) {
        archiveService.saveAll(archived)
        fungibleTokenContractsRepository.saveAll(existing)
    }

    open fun getExisting(address: String): IndexedFungibleTokenContracts? {
        return fungibleTokenContractsRepository.findByIdOrNull(address)
    }
}
