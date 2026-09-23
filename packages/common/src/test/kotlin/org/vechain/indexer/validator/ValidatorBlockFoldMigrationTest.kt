package org.vechain.indexer.validator

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.vechain.indexer.config.postgres.PostgresConfig

/** V36 on a database V35 left: the slots move and validator never resumes past a missing one. */
class ValidatorBlockFoldMigrationTest {

    @Test
    fun `validator resumes where validator_block stopped when it is a block behind`() {
        val (checkpoint, slots) = migrate(validator = 100, validatorBlock = 99, prunedBelow = 50)

        assertEquals(99L, checkpoint)
        assertEquals(listOf(99L), slots)
    }

    @Test
    fun `validator keeps its checkpoint when validator_block is behind its prune horizon`() {
        val (checkpoint, _) = migrate(validator = 100, validatorBlock = 40, prunedBelow = 50)

        assertEquals(100L, checkpoint)
    }

    /** validator's checkpoint after V36, and the slot blocks it moved into `validator.slot`. */
    private fun migrate(
        validator: Long,
        validatorBlock: Long,
        prunedBelow: Long,
    ): Pair<Long, List<Long>> =
        PostgreSQLContainer("postgres:16").use { container ->
            container.start()
            val dataSource =
                DriverManagerDataSource(container.jdbcUrl, container.username, container.password)
            flyway(dataSource, "35").migrate()
            val jdbc = JdbcTemplate(dataSource)
            jdbc.update(
                "INSERT INTO public.indexer_state (name, version, checkpoint_block, pruned_below) " +
                    "VALUES ('validator', 1, ?, ?), ('validator_block', 1, ?, NULL)",
                validator,
                prunedBelow,
                validatorBlock,
            )
            jdbc.update(
                "INSERT INTO validator_block.slot (block_number, validator, status, block_id, " +
                    "block_timestamp) VALUES (?, '\\x01', 'VALIDATED', '\\x02', 0)",
                validatorBlock,
            )

            flyway(dataSource, "36").migrate()

            val checkpoint =
                jdbc.queryForObject(
                    "SELECT checkpoint_block FROM public.indexer_state WHERE name = 'validator'",
                    Long::class.java,
                )!!
            assertEquals(
                0,
                jdbc.queryForObject(
                    "SELECT count(*) FROM public.indexer_state WHERE name = 'validator_block'",
                    Int::class.java,
                ),
            )
            checkpoint to
                jdbc.queryForList(
                    "SELECT block_number FROM validator.slot ORDER BY block_number",
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
