package org.vechain.indexer.service

import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.model.Contract
import org.vechain.indexer.repos.ContractRepo
import org.vechain.indexer.utils.HexUtil

@Service
open class ContractService(private val contractRepository: ContractRepo) {

    open fun findByAddress(address: String): Contract? {
        return contractRepository.findByIdOrNull(HexUtil.normalise(address))
    }

    open fun findByCreator(creator: String, pageable: Pageable): List<Contract> {
        return contractRepository.findAllByCreator(HexUtil.normalise(creator), pageable)
    }

}