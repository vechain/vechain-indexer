package org.vechain.indexer.stargate.vthoClaimed

import org.vechain.indexer.postgres.PostgresIndexedRepository

interface VthoClaimedByAccountRepository : PostgresIndexedRepository {
    fun saveAllVersioned(updated: List<VthoClaimedByAccount>, existing: List<VthoClaimedByAccount>)

    /** Find a record by its entity ID (account or account_tokenId). */
    fun findById(id: String): VthoClaimedByAccount?

    fun findAllById(ids: Collection<String>): List<VthoClaimedByAccount>
}
