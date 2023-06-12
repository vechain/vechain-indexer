package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.repository.TransferEventRepo
import org.vechain.indexer.utils.HexUtils

@Profile("transfer-events")
@Service
open class TransferEventService(private val transferEventRepo: TransferEventRepo) {

    fun find(address: String, tokenAddress: String, toPageable: Pageable): List<IndexedTransferEvent> {
        val addressNorm = HexUtils.normalise(address)
        val tokenAddressNorm = HexUtils.normalise(tokenAddress)
        return transferEventRepo.findByToOrFromAndTokenAddress(addressNorm, addressNorm, tokenAddressNorm, toPageable)
    }

    fun findByAddress(address: String, toPageable: Pageable): List<IndexedTransferEvent> {
        val addressNorm = HexUtils.normalise(address)
        return transferEventRepo.findByToOrFrom(addressNorm, addressNorm, toPageable)
    }

    fun findByTokenAddress(tokenAddress: String, toPageable: Pageable): List<IndexedTransferEvent> {
        val tokenAddressNorm = HexUtils.normalise(tokenAddress)
        return transferEventRepo.findByTokenAddress(tokenAddressNorm, toPageable)
    }

    fun findByTo(to: String, tokenAddress: String?, pageable: Pageable): List<IndexedTransferEvent> {
        val toNorm = HexUtils.normalise(to)
        return if (tokenAddress != null) {
            val tokenAddressNorm = HexUtils.normalise(tokenAddress)
            transferEventRepo.findByToAndTokenAddress(toNorm, tokenAddressNorm, pageable)
        } else {
            transferEventRepo.findByTo(toNorm, pageable)
        }
    }

    fun findByFrom(from: String, tokenAddress: String?, pageable: Pageable): List<IndexedTransferEvent> {
        val fromNorm = HexUtils.normalise(from)
        return if (tokenAddress != null) {
            val tokenAddressNorm = HexUtils.normalise(tokenAddress)
            transferEventRepo.findByFromAndTokenAddress(fromNorm, tokenAddressNorm, pageable)
        } else {
            transferEventRepo.findByFrom(fromNorm, pageable)
        }
    }
}
