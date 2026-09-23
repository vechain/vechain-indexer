package org.vechain.indexer.postgres

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.vechain.indexer.config.postgres.PostgresConfig

/** V36 and V37 on the database before them: the table moves, the parent never skips its rows. */
class FoldMigrationTest {

    private val validatorBlock =
        Fold(
            "36",
            "validator",
            "validator_block",
            "INSERT INTO validator_block.slot (block_number, validator, status, block_id, " +
                "block_timestamp) VALUES (?, '\\x01', 'VALIDATED', '\\x02', 0)",
            "validator.slot",
        )

    private val vetDelegated =
        Fold(
            "37",
            "delegation",
            "vet_delegated",
            "INSERT INTO vet_delegated.total_by_block (block_number, block_id, block_timestamp, " +
                "total, total_nft_count, hour_of_day, day_of_month, week_of_year, month, year, " +
                "time_frames, by_level, nft_count_by_level) " +
                "VALUES (?, '\\x02', 0, 0, 0, 0, 0, 0, 0, 0, '{}', '{}', '{}')",
            "delegation.total_by_block",
        )

    @Test
    fun `validator resumes where validator_block stopped when it is a block behind`() {
        assertEquals(99L to listOf(99L), validatorBlock.migrate(100, 99, prunedBelow = 50))
    }

    @Test
    fun `validator keeps its checkpoint when validator_block is behind its prune horizon`() {
        assertEquals(100L, validatorBlock.migrate(100, 40, prunedBelow = 50).first)
    }

    @Test
    fun `delegation resumes where vet_delegated stopped when it is a block behind`() {
        assertEquals(99L to listOf(99L), vetDelegated.migrate(100, 99, prunedBelow = 50))
    }

    @Test
    fun `delegation keeps its checkpoint when vet_delegated is behind its prune horizon`() {
        assertEquals(100L, vetDelegated.migrate(100, 40, prunedBelow = 50).first)
    }

    private class Fold(
        val version: String,
        val parent: String,
        val child: String,
        val insertChildRow: String,
        val movedTable: String,
    ) {
        /** The parent's checkpoint after [version], and the blocks now in [movedTable]. */
        fun migrate(parentAt: Long, childAt: Long, prunedBelow: Long): Pair<Long, List<Long>> =
            PostgreSQLContainer("postgres:16").use { container ->
                container.start()
                val dataSource =
                    DriverManagerDataSource(
                        container.jdbcUrl,
                        container.username,
                        container.password,
                    )
                flyway(dataSource, (version.toInt() - 1).toString()).migrate()
                val jdbc = JdbcTemplate(dataSource)
                jdbc.update(
                    "INSERT INTO public.indexer_state (name, version, checkpoint_block, " +
                        "pruned_below) VALUES (?, 1, ?, ?), (?, 1, ?, NULL)",
                    parent,
                    parentAt,
                    prunedBelow,
                    child,
                    childAt,
                )
                jdbc.update(insertChildRow, childAt)

                flyway(dataSource, version).migrate()

                assertEquals(
                    0,
                    jdbc.queryForObject(
                        "SELECT count(*) FROM public.indexer_state WHERE name = ?",
                        Int::class.java,
                        child,
                    ),
                )
                jdbc.queryForObject(
                    "SELECT checkpoint_block FROM public.indexer_state WHERE name = ?",
                    Long::class.java,
                    parent,
                )!! to
                    jdbc.queryForList(
                        "SELECT block_number FROM $movedTable ORDER BY block_number",
                        Long::class.java,
                    )
            }

        private fun flyway(dataSource: DriverManagerDataSource, target: String): Flyway =
            Flyway.configure()
                .dataSource(dataSource)
                .locations(PostgresConfig.MIGRATIONS)
                .target(target)
                .load()
    }
}
