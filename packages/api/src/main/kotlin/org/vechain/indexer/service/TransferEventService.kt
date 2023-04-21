package org.vechain.indexer.service

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.TransferEvent
import org.vechain.indexer.repos.TransferEventRepo

@Service
open class TransferEventService(private val transferEventRepo: TransferEventRepo) {

    open fun findAll(pageable: Pageable): List<TransferEvent> {
        return transferEventRepo.findAllBy(pageable)
    }

    fun find(address: String?, contractAddress: String?, toPageable: Pageable): List<TransferEvent> {
        return if (address != null && contractAddress != null) {
            transferEventRepo.findByToOrFromAndTokenAddress(address, address, contractAddress, toPageable).toList()
        } else if (address != null) {
            transferEventRepo.findByToOrFrom(address, address, toPageable).toList()
        } else if (contractAddress != null) {
            transferEventRepo.findByTokenAddress(contractAddress, toPageable).toList()
        } else {
            transferEventRepo.findAll(toPageable).toList()
        }
    }

}