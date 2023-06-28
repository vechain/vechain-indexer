package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.model.Address
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.model.rest.ContractType
import org.vechain.indexer.repository.ContractRepository

@Profile("contracts")
@Service
open class ContractService(private val contractRepository: ContractRepository) {

    open fun findByAddress(address: Address): IndexedContract? {
        return contractRepository.findByIdOrNull(address.value)
    }

    open fun find(
        creator: Address?,
        contractType: ContractType?,
        pageable: Pageable
    ): Page<IndexedContract> {
        return contractRepository.findByCreatorAndType(creator?.value, contractType, pageable)
    }
}
