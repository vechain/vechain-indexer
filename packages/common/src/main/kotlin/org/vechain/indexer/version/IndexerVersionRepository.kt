package org.vechain.indexer.version

/** Repository interface for managing IndexerVersion entities in PostgreSQL. */
interface IndexerVersionRepository {
    fun findById(indexerName: String): IndexerVersion?

    fun findByTableName(tableName: String): IndexerVersion?

    fun save(indexerVersion: IndexerVersion): IndexerVersion
}
