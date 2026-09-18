package org.vechain.indexer.stargate.tokenReward

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** The per-token reward page; the indexer reloads its trackers by validator instead. */
object TokenRewardIndexes {

    val SET =
        IndexSet(
            "token_reward",
            listOf(
                // /stargate/token-rewards/{tokenId}
                DeferrableIndex(
                    "state_current_token_idx",
                    "state",
                    "(token_id, reward_period, block_timestamp) WHERE superseded_at IS NULL",
                )
            ),
            needed =
                listOf(
                    // A block's supersede by id; the key would read every retained version first.
                    DeferrableIndex(
                        "state_current_id_idx",
                        "state",
                        "(id) WHERE superseded_at IS NULL",
                    )
                ),
        )
}
