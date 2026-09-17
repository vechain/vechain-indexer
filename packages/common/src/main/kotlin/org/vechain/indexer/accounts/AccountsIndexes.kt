package org.vechain.indexer.accounts

import org.vechain.indexer.postgres.IndexSet
import org.vechain.indexer.timeseries.SeriesIndexes

/** Only the totals series; the overview and the balance changelog carry no API-only index. */
object AccountsIndexes {
    val SET = IndexSet("accounts", SeriesIndexes.of("totals"))
}
