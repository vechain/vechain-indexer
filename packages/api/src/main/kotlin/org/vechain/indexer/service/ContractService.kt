package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.model.rest.ContractType
import org.vechain.indexer.repository.ContractRepository
import org.vechain.indexer.utils.HexUtils

@Profile("contracts")
@Service
open class ContractService(private val contractRepository: ContractRepository) {

    open fun findByAddress(address: String): IndexedContract? {
        return contractRepository.findByIdOrNull(HexUtils.normalise(address))
    }

    open fun find(creator: String?, contractType: ContractType?, pageable: Pageable): Page<IndexedContract> {
        return contractRepository.findByCreatorAndType(
            if (creator != null) HexUtils.normalise(creator) else null,
            contractType,
            pageable
        )
    }

}
