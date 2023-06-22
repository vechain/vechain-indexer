package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.repository.TransferEventRepository
import org.vechain.indexer.utils.HexUtils

@Profile("transfer-events")
@Service
open class TransferEventService(
    private val transferEventRepository: TransferEventRepository,
) {

    fun find(address: String, tokenAddress: String, pageable: Pageable): Page<IndexedTransferEvent> {
        return transferEventRepository.findByToOrFromAndTokenAddress(
            HexUtils.normalise(address),
            HexUtils.normalise(tokenAddress),
            pageable
        )
    }

    fun findByAddress(address: String, pageable: Pageable): Page<IndexedTransferEvent> {
        return transferEventRepository.findByToOrFrom(HexUtils.normalise(address), pageable)
    }

    fun findByTokenAddress(tokenAddress: String, pageable: Pageable): Page<IndexedTransferEvent> {
        return transferEventRepository.findByTokenAddress(HexUtils.normalise(tokenAddress), pageable)
    }

    fun findByTo(to: String, tokenAddress: String?, pageable: Pageable): Page<IndexedTransferEvent> {
        val toNorm = HexUtils.normalise(to)

        return if (tokenAddress != null) {
            val tokenAddressNorm = HexUtils.normalise(tokenAddress)
            transferEventRepository.findByToAndTokenAddress(toNorm, tokenAddressNorm, pageable)
        } else {
            transferEventRepository.findByTo(toNorm, pageable)
        }
    }

    fun findByFrom(from: String, tokenAddress: String?, pageable: Pageable): Page<IndexedTransferEvent> {
        val fromNorm = HexUtils.normalise(from)

        return if (tokenAddress != null) {
            val tokenAddressNorm = HexUtils.normalise(tokenAddress)
            transferEventRepository.findByFromAndTokenAddress(fromNorm, tokenAddressNorm, pageable)
        } else {
            transferEventRepository.findByFrom(fromNorm, pageable)
        }
    }
}
