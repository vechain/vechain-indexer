package org.vechain.indexer.stargate.token

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** The owner and manager pages; the indexer finds a token by validator or due period. */
object StargateTokenIndexes {

    val SET =
        IndexSet(
            "stargate_token",
            listOf(
                // /stargate/tokens?owner= and ?manager=
                current("state_current_owner_idx", "(owner)"),
                current("state_current_manager_idx", "(manager)"),
                // The unfiltered page, newest first.
                current("state_current_block_idx", "(block_number, token_id)"),
            ),
        )

    private fun current(name: String, columns: String) =
        DeferrableIndex(name, "state", "$columns WHERE superseded_at IS NULL")
}
