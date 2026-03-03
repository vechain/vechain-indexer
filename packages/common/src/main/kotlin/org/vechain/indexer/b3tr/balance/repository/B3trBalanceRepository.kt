package org.vechain.indexer.b3tr.balance.repository

import org.springframework.context.annotation.Profile
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.b3tr.balance.B3trBalance

@Profile("b3tr", "b3tr-balance")
interface B3trBalanceRepository : BaseIndexedRepository<B3trBalance, String>
