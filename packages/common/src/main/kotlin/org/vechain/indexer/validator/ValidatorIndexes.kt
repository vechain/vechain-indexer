package org.vechain.indexer.validator

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** The /validators stake order and four /block-rewards pages; slot partials are the indexer's. */
object ValidatorIndexes {

    val SET =
        IndexSet(
            "validator",
            listOf(
                DeferrableIndex(
                    "state_current_stake_idx",
                    "state",
                    "(validator_vet_staked DESC, id) WHERE superseded_at IS NULL",
                ),
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
