package org.vechain.indexer.b3tr.treasury

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("b3tr", "b3tr-treasury")
@Repository
interface TreasuryTransferRepository : BaseIndexedRepository<TreasuryTransfer, String>
