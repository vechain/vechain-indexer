package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.Address
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.repository.TransferEventRepository

@Profile("transfer-events")
@Service
open class TransferEventService(
    private val transferEventRepository: TransferEventRepository,
) {

    fun find(
        address: Address,
        tokenAddress: Address,
        pageable: Pageable
    ): Page<IndexedTransferEvent> {
        return transferEventRepository.findByToOrFromAndTokenAddress(
            address.value,
            tokenAddress.value,
            pageable
        )
    }

    fun findByAddress(address: Address, pageable: Pageable): Page<IndexedTransferEvent> {
        return transferEventRepository.findByToOrFrom(address.value, pageable)
    }

    fun findByTokenAddress(tokenAddress: Address, pageable: Pageable): Page<IndexedTransferEvent> {
        return transferEventRepository.findByTokenAddress(tokenAddress.value, pageable)
    }

    fun findByTo(
        to: Address,
        tokenAddress: Address?,
        pageable: Pageable
    ): Page<IndexedTransferEvent> {
        return if (tokenAddress != null) {
            transferEventRepository.findByToAndTokenAddress(to.value, tokenAddress.value, pageable)
        } else {
            transferEventRepository.findByTo(to.value, pageable)
        }
    }

    fun findByFrom(
        from: Address,
        tokenAddress: Address?,
        pageable: Pageable
    ): Page<IndexedTransferEvent> {
        return if (tokenAddress != null) {
            transferEventRepository.findByFromAndTokenAddress(
                from.value,
                tokenAddress.value,
                pageable
            )
        } else {
            transferEventRepository.findByFrom(from.value, pageable)
        }
    }

    fun findByBlockNumber(
        blockNumber: Long,
        addresses: List<Address>,
        toPageable: Pageable
    ): Page<IndexedTransferEvent> {
        return transferEventRepository.findByBlockNumberAndToOrFromIn(
            blockNumber,
            addresses.map { it.value },
            toPageable
        )
    }
}
