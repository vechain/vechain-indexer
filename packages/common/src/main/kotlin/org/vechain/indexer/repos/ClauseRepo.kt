package org.vechain.indexer.repos

import org.springframework.stereotype.Repository
import org.vechain.indexer.model.WrappedClause

@Repository
interface ClauseRepo : IndexerRepo<WrappedClause>
