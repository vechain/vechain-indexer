package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.TransferEvent
import org.vechain.indexer.repos.TransferEventRepo
import org.vechain.indexer.utils.HexUtil

@Profile("transfer-event-indexer")
@Service
open class TransferEventService(private val transferEventRepo: TransferEventRepo) {

    fun find(address: String, tokenAddress: String, toPageable: Pageable): List<TransferEvent> {
        val addressNorm = HexUtil.normalise(address)
        val tokenAddressNorm = HexUtil.normalise(tokenAddress)
        return transferEventRepo.findByToOrFromAndTokenAddress(addressNorm, addressNorm, tokenAddressNorm, toPageable)

    }

    fun findByAddress(address: String, toPageable: Pageable): List<TransferEvent> {
        val addressNorm = HexUtil.normalise(address)
        return transferEventRepo.findByToOrFrom(addressNorm, addressNorm, toPageable)
    }

    fun findByTokenAddress(tokenAddress: String, toPageable: Pageable): List<TransferEvent> {
        val tokenAddressNorm = HexUtil.normalise(tokenAddress)
        return transferEventRepo.findByTokenAddress(tokenAddressNorm, toPageable)
    }

    fun findByTo(to: String, tokenAddress: String?, pageable: Pageable): List<TransferEvent> {
        val toNorm = HexUtil.normalise(to)
        return if (tokenAddress != null) {
            val tokenAddressNorm = HexUtil.normalise(tokenAddress)
            transferEventRepo.findByToAndTokenAddress(toNorm, tokenAddressNorm, pageable)
        } else {
            transferEventRepo.findByTo(toNorm, pageable)
        }
    }

    fun findByFrom(from: String, tokenAddress: String?, pageable: Pageable): List<TransferEvent> {
        val fromNorm = HexUtil.normalise(from)
        return if (tokenAddress != null) {
            val tokenAddressNorm = HexUtil.normalise(tokenAddress)
            transferEventRepo.findByFromAndTokenAddress(fromNorm, tokenAddressNorm, pageable)
        } else {
            transferEventRepo.findByFrom(fromNorm, pageable)
        }
    }
}