package org.vechain.indexer.service

import org.springframework.stereotype.Service
import org.vechain.indexer.model.Contract
import org.vechain.indexer.repos.ContractRepo
import org.vechain.indexer.utils.HexUtil
import org.vechain.indexer.validation.Validation

@Service
open class ContractService(private val contractRepository: ContractRepo) {

    open fun findByCreator(creator: String): List<Contract> {

        Validation.checkAddress(creator)

        return contractRepository.findAllByCreator(HexUtil.normalise(creator))
    }

}