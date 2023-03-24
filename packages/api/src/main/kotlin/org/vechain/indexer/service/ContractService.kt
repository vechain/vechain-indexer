package org.vechain.indexer.service

import org.springframework.stereotype.Service
import org.vechain.indexer.model.Contract
import org.vechain.indexer.repos.ContractRepo
import org.vechain.indexer.utils.HexUtil

@Service
class ContractService(private val contractRepository: ContractRepo) {

    fun findByCreator(creator: String): List<Contract> {
        return contractRepository.findAllByCreator(HexUtil.normalise(creator))
    }

}