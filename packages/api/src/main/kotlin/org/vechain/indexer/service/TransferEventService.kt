package org.vechain.indexer.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.TransferEvent
import org.vechain.indexer.repos.TransferEventRepo

@Service
open class TransferEventService(private val transferEventRepo: TransferEventRepo) {

    fun find(address: String?, tokenAddress: String?, toPageable: Pageable): Page<TransferEvent> {
        return if (address != null && tokenAddress != null) {
            transferEventRepo.findByToOrFromAndTokenAddress(address, address, tokenAddress, toPageable)
        } else if (address != null) {
            transferEventRepo.findByToOrFrom(address, address, toPageable)
        } else if (tokenAddress != null) {
            transferEventRepo.findByTokenAddress(tokenAddress, toPageable)
        } else {
            throw IllegalArgumentException("Either address or tokenAddress must be provided")
        }
    }

}