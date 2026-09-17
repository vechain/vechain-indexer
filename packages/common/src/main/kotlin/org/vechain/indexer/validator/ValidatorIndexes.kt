package org.vechain.indexer.validator

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** Only the stake ordering /validators pages by; the missed-slot lookup is the indexer's. */
object ValidatorIndexes {

    val SET =
        IndexSet(
            "validator",
            listOf(
                DeferrableIndex(
                    "state_current_stake_idx",
                    "state",
                    "(validator_vet_staked DESC, id) WHERE superseded_at IS NULL",
                )
            ),
        )
}
