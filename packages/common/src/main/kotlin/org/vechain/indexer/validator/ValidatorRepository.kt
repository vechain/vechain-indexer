package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository

@Profile("validator")
@Repository
interface ValidatorRepository : BasePagingAndSortingIndexedRepository<Validator, String>
