package org.vechain.indexer.version

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface IndexerVersionRepository : CrudRepository<IndexerVersion, String> {
    fun findByCollectionName(collectionName: String): IndexerVersion?
}
