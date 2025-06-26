package org.vechain.indexer.repository.stargate

import org.springframework.context.annotation.Profile
import org.vechain.indexer.model.stargate.TotalVthoClaimedByAccount
import org.vechain.indexer.repository.BaseIndexedRepository

@Profile("stargate")
interface TotalVthoClaimedByAccountRepository :
    BaseIndexedRepository<TotalVthoClaimedByAccount, String>
