package org.vechain.indexer.b3tr.challenges

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** The public list and a wallet's list; the round and member lookups are read here. */
object ChallengeIndexes {

    val SET =
        IndexSet(
            "b3tr_challenges",
            listOf(
                // /b3tr/challenges, whole or narrowed to one status.
                current(
                    "challenge_current_public_idx",
                    "challenge",
                    "(visibility, created_at_block_timestamp, challenge_id)",
                ),
                current(
                    "challenge_current_public_status_idx",
                    "challenge",
                    "(visibility, status, created_at_block_timestamp, challenge_id)",
                ),
                // A wallet's challenges, newest challenge first.
                current(
                    "user_challenge_current_wallet_idx",
                    "user_challenge",
                    "(wallet, challenge_created_at_block_timestamp, challenge_id)",
                ),
            ),
        )

    private fun current(name: String, table: String, columns: String) =
        DeferrableIndex(name, table, "$columns WHERE superseded_at IS NULL")
}
