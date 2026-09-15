package org.vechain.indexer.b3tr.action

import org.vechain.indexer.b3tr.action.response.ERROR_CANT_PASS_ROUND_AND_DATE
import org.vechain.indexer.exception.BadRequestException

/** The period a request names; all time when it names neither a round nor a date. */
fun requestedPeriod(roundId: Int?, date: String?): ActionPeriod {
    if (roundId != null && date != null) throw BadRequestException(ERROR_CANT_PASS_ROUND_AND_DATE)
    return ActionPeriod.of(roundId, date)
}
