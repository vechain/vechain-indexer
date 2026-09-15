package org.vechain.indexer.b3tr.richlist

import java.math.BigDecimal
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.b3tr.balance.B3trBalanceReadRepository

@Profile("b3tr", "b3tr-balance")
@Service
open class B3trRichlistCountService(private val repository: B3trBalanceReadRepository) {

    @Cacheable(value = ["b3tr_richlist_total_holders"], key = "#scope.name()", sync = true)
    open fun getPositiveHolderCount(scope: RichlistScope): Long =
        countBalancesGreaterThan(scope, BigDecimal.ZERO)

    @CachePut(value = ["b3tr_richlist_total_holders"], key = "#scope.name()")
    open fun refreshPositiveHolderCount(scope: RichlistScope): Long =
        countBalancesGreaterThan(scope, BigDecimal.ZERO)

    open fun countBalancesGreaterThan(scope: RichlistScope, threshold: BigDecimal): Long =
        repository.countGreaterThan(scope.column, threshold)
}
