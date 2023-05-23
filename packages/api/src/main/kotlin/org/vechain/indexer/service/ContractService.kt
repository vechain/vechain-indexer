package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.Contract
import org.vechain.indexer.model.rest.ContractType
import org.vechain.indexer.repos.ContractRepoImpl
import org.vechain.indexer.utils.HexUtil

@Profile("contract-indexer")
@Service
open class ContractService(
    private val contractRepoImpl: ContractRepoImpl
) {

    open fun findByAddress(address: String): Contract? {
        return contractRepoImpl.findById(HexUtil.normalise(address))
    }

    open fun find(
        creator: String?,
        contractType: ContractType?,
        pageable: Pageable
    ): List<Contract> {
        return contractRepoImpl.findByCreatorAndType(
            if (creator != null) HexUtil.normalise(creator) else null,
            contractType,
            pageable
        )
    }

}