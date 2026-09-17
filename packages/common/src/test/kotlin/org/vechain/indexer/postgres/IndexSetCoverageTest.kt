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
            )
    }
}
