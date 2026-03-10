package org.vechain.indexer.accounts

@Deprecated("V1 total-accounts query shape is deprecated. Use /api/v2/accounts/totals instead.")
enum class AccountQueryTimeFrame {
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ALL,
}
