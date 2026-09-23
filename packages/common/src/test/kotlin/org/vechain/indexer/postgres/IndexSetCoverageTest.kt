package org.vechain.indexer.postgres

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** Every index of a declared schema is deferrable or named here; a new one fails until it is. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndexSetCoverageTest {

    private val database = PostgresTestDatabase()

    @BeforeAll fun start() = database.start().let {}

    @AfterAll fun stop() = database.close()

    fun sets() = IndexSets.ALL.map { Named.of(it.schema, it) }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sets")
    fun `what a drop leaves is what the indexer itself reads`(set: IndexSet) {
        val builder = IndexBuilder(database.properties)
        try {
            builder.drop(set)

            assertEquals(NEEDED.getValue(set.schema).sorted(), standing(set.schema).sorted())
        } finally {
            builder.build(set)
        }
    }

    private fun standing(schema: String): List<String> =
        database.jdbc.query(
            "SELECT indexname FROM pg_indexes WHERE schemaname = ?",
            { rs, _ -> rs.getString(1) },
            schema,
        )

    private companion object {
        /** Rollback by block, prune by horizon, and the current-row lookup a supersede needs. */
        val NEEDED =
            mapOf(
                "history" to
                    listOf("event_block_idx", "event_lifecycle_idx", "event_address_block_idx"),
                "b3tr_action" to
                    listOf(
                            "entity_all_time",
                            "entity_daily",
                            "entity_round",
                            "app_user_all_time",
                            "app_user_daily",
                            "app_user_round",
                        )
                        .flatMap {
                            listOf(
                                "${it}_pkey",
                                "${it}_current_idx",
                                "${it}_block_idx",
                                "${it}_prune_idx",
                            )
                        },
                // The keys two foreign keys point at, and the ones their cascade deletes read.
                "blocks" to
                    listOf(
                        "block_pkey",
                        "transaction_pkey",
                        "transaction_block_idx",
                        "clause_pkey",
                        "event_pkey",
                        "transfer_pkey",
                    ),
                // Rollback by block, and the key that keeps a wallet's first touch of a token.
                "transfers" to
                    listOf(
                        "transfer_block_idx",
                        "token_interaction_pkey",
                        "token_interaction_block_idx",
                    ),
                "stargate_token" to listOf("state_pkey", "state_block_idx", "state_prune_idx"),
                // The reload of a validator's cycle trackers after a restart.
                "token_reward" to
                    listOf(
                        "state_pkey",
                        "state_current_id_idx",
                        "state_current_validator_idx",
                        "state_block_idx",
                        "state_prune_idx",
                    ),
                // A series table is read by block alone, so only its key stands.
                "vet_delegated" to listOf("total_by_block_pkey"),
                "stargate_vtho_generated" to listOf("total_by_block_pkey"),
                "stargate_vtho_claimed" to
                    listOf(
                        "total_by_block_pkey",
                        "claimed_by_token_pkey",
                        "claimed_by_token_block_idx",
                        "claimed_by_token_prune_idx",
                    ),
                "stargate_staking" to
                    listOf(
                        "vet_staked_by_block_pkey",
                        "nft_holders_by_block_pkey",
                        "owner_balance_pkey",
                        "owner_balance_block_idx",
                    ),
                // The four sampled slot partials latestSampled reads, one per resolution.
                "validator" to
                    listOf(
                        "state_pkey",
                        "state_block_idx",
                        "state_prune_idx",
                        "slot_pkey",
                        "slot_hourly_idx",
                        "slot_daily_idx",
                        "slot_weekly_idx",
                        "slot_monthly_idx",
                    ),
                // The due, zero-cycle, by-token and by-validator scans the indexer makes.
                "delegation" to
                    listOf(
                        "state_pkey",
                        "state_current_validator_idx",
                        "state_current_token_idx",
                        "state_current_transition_idx",
                        "state_block_idx",
                        "state_prune_idx",
                    ),
                // Nothing but rollback, prune and the key: every read is by the key itself.
                "b3tr_balance" to listOf("state_pkey", "state_block_idx", "state_prune_idx"),
                "b3tr_gm" to listOf("state_pkey", "state_block_idx", "state_prune_idx"),
                "b3tr_treasury" to listOf("transfer_pkey", "transfer_block_idx"),
                "b3tr_x_alloc" to listOf("result_pkey", "result_block_idx", "result_prune_idx"),
                // The round boundary re-derives a status, and a completion fans out over members.
                "b3tr_challenges" to
                    listOf("challenge", "challenge_member", "user_challenge").flatMap {
                        listOf("${it}_pkey", "${it}_block_idx", "${it}_prune_idx")
                    } +
                        listOf(
                            "challenge_current_idx",
                            "challenge_current_start_round_idx",
                            "challenge_current_end_round_idx",
                            "challenge_member_current_idx",
                            "user_challenge_current_challenge_idx",
                            "challenge_app_pkey",
                            "challenge_app_block_idx",
                        ),
                // The exits due this block and the delegations one of them ends.
                "b3tr_navigator" to
                    listOf("navigator", "citizen", "fee").flatMap {
                        listOf(
                            "${it}_pkey",
                            "${it}_current_idx",
                            "${it}_block_idx",
                            "${it}_prune_idx",
                        )
                    } +
                        listOf(
                            "navigator_exit_idx",
                            "citizen_navigator_idx",
                            "delegation_event_pkey",
                            "delegation_event_block_idx",
                        ),
                // findCurrentByStates leads on state, so that page is the indexer's too.
                "b3tr_proposal" to
                    listOf(
                        "result_pkey",
                        "result_current_idx",
                        "result_current_state_idx",
                        "result_block_idx",
                        "result_prune_idx",
                        "comment_pkey",
                        "comment_block_idx",
                    ),
                // Four foreign keys point at safe.proxy; their cascade reads each key's safe
                // prefix.
                "safe" to
                    listOf(
                        "proxy_pkey",
                        "proxy_block_idx",
                        "membership_pkey",
                        "membership_block_idx",
                        "membership_prune_idx",
                        "tx_state_pkey",
                        "tx_state_current_idx",
                        "tx_state_block_idx",
                        "tx_state_prune_idx",
                        "tx_approval_pkey",
                        "tx_approval_block_idx",
                        "tx_proposal_pkey",
                        "tx_proposal_block_idx",
                        "tx_proposal_prune_idx",
                        "tx_subcall_pkey",
                        "tx_subcall_block_idx",
                    ),
                // The overview sweep is a scan by design; nothing else here is the API's alone.
                "accounts" to
                    listOf(
                        "overview_pkey",
                        "overview_block_idx",
                        "overview_prune_idx",
                        "vet_balance_pkey",
                        "vet_balance_block_idx",
                        "totals_pkey",
                        "seen_pkey",
                        "seen_block_idx",
                    ),
                // The daily rollup the indexer reads back per day, and the origins it counts.
                "explorer" to
                    listOf(
                        "block_usage_pkey",
                        "daily_fees_pkey",
                        "daily_fees_current_idx",
                        "daily_fees_block_idx",
                        "daily_fees_prune_idx",
                        "daily_origin_pkey",
                        "daily_origin_block_idx",
                    ),
                "contracts" to listOf("state_pkey", "state_block_idx", "state_prune_idx"),
                "nft" to listOf("ownership_pkey", "ownership_block_idx", "ownership_prune_idx"),
                "nft_blacklist" to listOf("collection_state_pkey", "collection_state_block_idx"),
                // The running weight per (proposal, support), which every vote reads back.
                "vevote" to
                    listOf(
                        "comment_pkey",
                        "comment_block_idx",
                        "result_pkey",
                        "result_current_proposal_idx",
                        "result_block_idx",
                        "result_prune_idx",
                    ),
                // Three foreign keys point at proposal; their cascade reads each child's prefix.
                "vevote_historic" to
                    listOf(
                        "proposal_pkey",
                        "proposal_block_idx",
                        "proposal_choice_pkey",
                        "proposal_tally_pkey",
                        "description_pkey",
                        "description_block_idx",
                        "vote_pkey",
                        "vote_block_idx",
                    ),
            )
    }
}
