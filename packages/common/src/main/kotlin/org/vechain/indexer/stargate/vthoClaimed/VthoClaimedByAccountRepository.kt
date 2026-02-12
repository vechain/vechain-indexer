package org.vechain.indexer.stargate.vthoClaimed

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Query
import org.vechain.indexer.BaseIndexedRepository

@Profile("stargate", "vtho-claimed-by-account")
interface VthoClaimedByAccountRepository : BaseIndexedRepository<VthoClaimedByAccount, String> {
    @Query("{ 'account': ?0, 'tokenId': null }")
    fun findByAccountAndTokenIdIsNull(account: String): List<VthoClaimedByAccount>
}
