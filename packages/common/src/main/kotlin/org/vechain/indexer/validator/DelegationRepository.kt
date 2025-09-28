package org.vechain.indexer.stargate

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.validator.Validator

@Profile("delegation")
@Repository
interface DelegationRepository : BasePagingAndSortingIndexedRepository<Validator, String>
