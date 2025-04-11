package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedTransaction

@Profile("vevote-events")
@Repository
interface VevoteCommentRepository : BaseIndexedRepository<IndexedTransaction> {}
