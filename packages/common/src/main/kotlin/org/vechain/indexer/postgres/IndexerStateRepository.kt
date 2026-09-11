package org.vechain.indexer.postgres

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.thor.model.BlockIdentifier

/** `public.indexer_state`: one row per schema with its version and resume point. */
@Repository
@ConditionalOnPostgres
open class IndexerStateRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {

    open fun storedVersion(schema: String): Int? =
        jdbc
            .query(
                "SELECT version FROM public.indexer_state WHERE name = ?",
                { rs, _ -> rs.getInt(1) },
                schema,
            )
            .firstOrNull()

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun recordVersion(schema: String, version: Int) {
        jdbc.update(
            "INSERT INTO public.indexer_state (name, version) VALUES (?, ?) " +
                "ON CONFLICT (name) DO UPDATE SET version = EXCLUDED.version",
            schema,
            version,
        )
    }

    /** Empties the schema's tables and records [version]; the next run backfills from the start. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun resync(schema: String, version: Int, tables: PostgresIndexerTables) {
        tables.truncate()
        jdbc.update(
            "INSERT INTO public.indexer_state (name, version, checkpoint_block, checkpoint_block_id) " +
                "VALUES (?, ?, NULL, NULL) ON CONFLICT (name) DO UPDATE " +
                "SET version = EXCLUDED.version, checkpoint_block = NULL, checkpoint_block_id = NULL",
            schema,
            version,
        )
    }

    open fun checkpoint(schema: String): BlockIdentifier? =
        jdbc
            .query(
                "SELECT checkpoint_block, checkpoint_block_id FROM public.indexer_state " +
                    "WHERE name = ? AND checkpoint_block IS NOT NULL",
                { rs, _ ->
                    BlockIdentifier(rs.getLong(1), PostgresHex.hexOrNull(rs.getBytes(2)))
                },
                schema,
            )
            .firstOrNull()

    /** Joins the caller's transaction when there is one, so data and checkpoint land together. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun saveCheckpoint(schema: String, block: BlockIdentifier) {
        jdbc.update(
            "UPDATE public.indexer_state SET checkpoint_block = ?, checkpoint_block_id = ? " +
                "WHERE name = ?",
            block.number,
            PostgresHex.bytesOrNull(block.id),
            schema,
        )
    }
}
