package org.vechain.indexer.stargate.nftHolders

interface NftOwnerBalanceRepository {
    fun saveAllVersioned(updated: List<NftOwnerBalance>, existing: List<NftOwnerBalance>)

    fun findByOwnerIn(owners: Collection<String>): List<NftOwnerBalance>

    fun rollback(blockNumber: Long)
}
