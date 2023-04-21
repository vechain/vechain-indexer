package org.vechain.indexer.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.TransferEvent
import org.vechain.indexer.repos.TransferEventRepo

@Service
open class TransferEventService(private val transferEventRepo: TransferEventRepo) {

    fun find(address: String?, contractAddress: String?, toPageable: Pageable): Page<TransferEvent> {
        return if (address != null && contractAddress != null) {
            transferEventRepo.findByToOrFromAndTokenAddress(address, address, contractAddress, toPageable)
        } else if (address != null) {
            transferEventRepo.findByToOrFrom(address, address, toPageable)
        } else if (contractAddress != null) {
            transferEventRepo.findByTokenAddress(contractAddress, toPageable)
        } else {
            transferEventRepo.findAll(toPageable)
        }
    }

}