package org.vechain.indexer.b3tr.gm.repository

import org.vechain.indexer.b3tr.gm.GMLevelOverview
import org.vechain.indexer.b3tr.gm.GmLevelName
import org.vechain.indexer.b3tr.gm.GmNft
import org.vechain.indexer.postgres.PostgresIndexedRepository
import org.vechain.indexer.thor.Address

interface GmNftRepository : PostgresIndexedRepository {
    // Versioned operations
    fun saveAllVersioned(updated: List<GmNft>, existing: List<GmNft>)

    // Query operations
    fun findById(id: String): GmNft?

    fun countByLevelAndOwnerNot(level: GmLevelName, owner: String = Address.ZERO_ADDRESS): Long

    fun levelCounts(): List<GMLevelOverview>
}
