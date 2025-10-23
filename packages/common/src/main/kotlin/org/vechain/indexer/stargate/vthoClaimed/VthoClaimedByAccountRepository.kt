package org.vechain.indexer.stargate.vthoClaimed

import org.springframework.context.annotation.Profile
import org.vechain.indexer.BaseIndexedRepository

@Profile("stargate", "vtho-claimed-by-account")
interface VthoClaimedByAccountRepository : BaseIndexedRepository<VthoClaimedByAccount, String>
