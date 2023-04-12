package org.vechain.indexer.service

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.TransferEvent
import org.vechain.indexer.repos.TransferEventRepo

@Service
open class TransferEventService(private val transferEventRepo: TransferEventRepo) {

    open fun findAll(pageable: Pageable): List<TransferEvent> {
        return transferEventRepo.findAll(pageable).toList()
    }

}