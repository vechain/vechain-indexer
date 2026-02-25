package org.vechain.indexer.b3tr.vot3.repository

import org.springframework.context.annotation.Profile
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.b3tr.vot3.Vot3Balance

@Profile("b3tr", "vot3-balance")
interface Vot3BalanceRepository : BaseIndexedRepository<Vot3Balance, String>
