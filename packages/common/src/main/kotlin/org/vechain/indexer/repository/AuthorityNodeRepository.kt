package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedAuthorityNode

@Profile("authority-nodes")
@Repository
interface AuthorityNodeRepository : BaseIndexedRepository<IndexedAuthorityNode> {}
