package org.vechain.indexer.repos

import org.springframework.stereotype.Repository
import org.vechain.indexer.model.TransferEvent

@Repository
interface TransferEventRepo : IndexerRepo<TransferEvent>