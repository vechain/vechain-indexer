package org.vechain.indexer.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.TransferEvent
import org.vechain.indexer.repos.TransferEventRepo
import org.vechain.indexer.utils.HexUtil

@Service
open class TransferEventService(private val transferEventRepo: TransferEventRepo) {

    fun find(address: String, tokenAddress: String, toPageable: Pageable): Page<TransferEvent> {
        val addressNorm = HexUtil.normalise(address)
        val tokenAddressNorm = HexUtil.normalise(tokenAddress)
        return transferEventRepo.findByToOrFromAndTokenAddress(addressNorm, addressNorm, tokenAddressNorm, toPageable)

    }

    fun findByAddress(address: String, toPageable: Pageable): Page<TransferEvent> {
        val addressNorm = HexUtil.normalise(address)
        return transferEventRepo.findByToOrFrom(addressNorm, addressNorm, toPageable)
    }

    fun findByTokenAddress(tokenAddress: String, toPageable: Pageable): Page<TransferEvent> {
        val tokenAddressNorm = HexUtil.normalise(tokenAddress)
        return transferEventRepo.findByTokenAddress(tokenAddressNorm, toPageable)
    }
}