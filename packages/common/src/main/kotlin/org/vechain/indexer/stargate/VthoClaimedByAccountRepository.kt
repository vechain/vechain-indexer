package org.vechain.indexer.stargate

import org.springframework.context.annotation.Profile
import org.vechain.indexer.BaseIndexedRepository

@Profile("stargate")
interface VthoClaimedByAccountRepository : BaseIndexedRepository<VthoClaimedByAccount, String>
