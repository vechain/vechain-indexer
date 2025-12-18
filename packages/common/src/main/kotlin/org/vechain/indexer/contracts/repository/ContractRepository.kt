package org.vechain.indexer.contracts.repository

import org.springframework.context.annotation.Profile
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.contracts.Contract

@Profile("contracts", "contract")
interface ContractRepository : BaseIndexedRepository<Contract, String> {}
