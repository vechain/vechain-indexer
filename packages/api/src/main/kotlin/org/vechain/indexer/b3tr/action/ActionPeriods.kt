package org.vechain.indexer.b3tr.action

import org.vechain.indexer.b3tr.action.response.ERROR_CANT_PASS_ROUND_AND_DATE
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.rest.CachePolicy

/** The period a request names; all time when it names neither a round nor a date. */
fun requestedPeriod(roundId: Int?, date: String?): ActionPeriod {
    if (roundId != null && date != null) throw BadRequestException(ERROR_CANT_PASS_ROUND_AND_DATE)
    return ActionPeriod.of(roundId, date)
}

/**
 * A round that has closed can never be awarded another action, so anything scoped to it is final.
 * The newest round on record is the one still open — or, if the indexer is behind a boundary, one
 * that has only just closed — so only the rounds behind it are settled; the rest keep [open].
 */
fun ActionReadRepository.cachePolicy(period: ActionPeriod, open: CachePolicy): CachePolicy {
    val roundId = period.roundId ?: return open
    val latestRound = latestRound()
    return if (latestRound != null && roundId < latestRound) CachePolicy.IMMUTABLE else open
}
