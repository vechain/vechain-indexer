package org.vechain.indexer.validators

/** Legacy window enum for the deprecated `/api/v1/validators/blocks/missed` endpoint. */
@Deprecated("Removed alongside /api/v1/validators/blocks/missed once clients migrate")
enum class MissedBlocksTimeframe {
    DAY,
    WEEK,
    MONTH,
    YEAR,
}
