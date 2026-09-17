package org.vechain.indexer.validator

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** The four /block-rewards pages; the sampled partials are the indexer's boundary cache. */
object ValidatorBlockIndexes {

    val SET =
        IndexSet(
            "validator_block",
            listOf(
                DeferrableIndex(
                    "slot_validator_status_block_idx",
                    "slot",
                    "(validator, status, block_number)",
                ),
                DeferrableIndex("slot_status_block_idx", "slot", "(status, block_number)"),
                DeferrableIndex(
                    "slot_validator_time_idx",
                    "slot",
                    "(validator, status, block_timestamp)",
                ),
                DeferrableIndex("slot_time_idx", "slot", "(block_timestamp)"),
            ),
        )
}
