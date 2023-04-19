package org.vechain.indexer.repos

import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.TransferEvent

@Repository
interface TransferEventRepo : BaseIndexedRepo<TransferEvent>, PagingAndSortingRepository<TransferEvent, String>