package org.vechain.indexer.b3tr.round

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("b3tr", "b3tr-actions", "b3tr-user-round-action-summary", "b3tr-app-round-action-summary")
open class RoundService {
    fun getRoundAtBlock(blockNumber: Long): Int {
        error("RoundService.getRoundAtBlock not implemented")
    }
}
