package org.vechain.indexer.contracts

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils.offsetSlice

@Profile("contracts")
@Service
open class ContractsService(private val repository: ContractReadRepository) {
    fun getByAddress(address: Address): Contract? = repository.findByAddress(address.value)

    fun getByMaster(address: Address, pageable: Pageable): Slice<Contract> =
        offsetSlice(pageable, Contract::createdOn.name) { offset, limit, direction ->
            repository.findByMaster(address.value, offset, limit, direction)
        }
}
