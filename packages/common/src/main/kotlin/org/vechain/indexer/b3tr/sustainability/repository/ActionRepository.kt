package org.vechain.indexer.b3tr.sustainability.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.sustainability.Action

@Profile("b3tr", "sustainability", "sustainable-actions")
@Repository
interface ActionRepository :
    BasePagingAndSortingIndexedRepository<Action, String>, CustomActionRepository {}
