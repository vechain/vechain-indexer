package org.vechain.indexer.transaction

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils

@Profile("transactions")
@Service
open class TransactionService(private val transactionRepository: TransactionRepository) {

    open fun findById(id: String): IndexedTransaction? {
        return transactionRepository.findById(HexUtils.normalise(id))
    }

    open fun findByOriginOrDelegator(
        address: Address,
        includeDelegated: Boolean,
        pageable: Pageable,
    ): Slice<IndexedTransaction> {
        return if (includeDelegated) {
            transactionRepository.findByOriginOrGasPayer(address.value, address.value, pageable)
        } else {
            transactionRepository.findByOrigin(address.value, pageable)
        }
    }

    open fun findAllDelegated(delegator: Address, pageable: Pageable): Slice<IndexedTransaction> {
        return transactionRepository.findByGasPayerAndOriginNot(
            delegator.value,
            delegator.value,
            pageable,
        )
    }

    open fun findByContractAddress(
        contractAddress: Address,
        pageable: Pageable,
    ): Slice<IndexedTransaction> {
        return transactionRepository.findByContractAddress(
            HexUtils.normalise(contractAddress.value),
            pageable,
        )
    }

    open fun findByContractAddresses(
        contractAddresses: List<Address>,
        pageable: Pageable,
    ): Slice<IndexedTransaction> {
        val normalizedAddresses = contractAddresses.map { HexUtils.normalise(it.value) }
        return transactionRepository.findByContractAddresses(normalizedAddresses, pageable)
    }
}
