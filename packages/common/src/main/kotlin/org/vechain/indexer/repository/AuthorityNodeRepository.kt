package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.AuthorityNode

@Profile("authority-nodes")
@Repository
interface AuthorityNodeRepository : BaseIndexedRepository<AuthorityNode> {}
