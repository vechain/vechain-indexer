package org.vechain.indexer.contracts

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.contracts.repository.ContractRepository
import org.vechain.indexer.thor.Address

@Profile("contracts")
@Service
open class ContractsService(private val contractRepository: ContractRepository) {
    fun getByAddress(address: Address): Contract? = contractRepository.findById(address.value)

    fun getByMaster(address: Address, pageable: Pageable): Slice<Contract> =
        contractRepository.findByMaster(address.value, pageable)
}
