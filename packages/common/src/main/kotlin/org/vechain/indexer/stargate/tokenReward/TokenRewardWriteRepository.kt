package org.vechain.indexer.stargate.tokenReward

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex
import org.vechain.indexer.postgres.PostgresIndexerTables

/** `token_reward.state` as a temporal table, plus the indexer's own reads of the current rows. */
@Repository
@ConditionalOnPostgres
open class TokenRewardWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    /** Applies the records block by block in ascending order, all in one transaction. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(rewards: List<TokenReward>) {
        rewards
            .groupBy { it.blockNumber }
            .toSortedMap()
            .forEach { (blockNumber, rows) -> saveBlock(blockNumber, rows) }
    }

    // `block_number < ?` keeps a replayed block from closing its own rows.
    private fun saveBlock(blockNumber: Long, rewards: List<TokenReward>) {
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = rewards.associateBy { it.id }.values.toList()
        jdbc.update(
            "UPDATE token_reward.state SET superseded_at = ? " +
                "WHERE id = ANY(?) AND superseded_at IS NULL AND block_number < ?",
            blockNumber,
            rows.map { it.id }.toTypedArray(),
            blockNumber,
        )
        jdbc.batchUpdate(INSERT, rows, rows.size) { ps, r -> TokenRewardRowMapping.bind(ps, r) }
    }

    open fun findAllByValidatorAndRewardPeriodAndCycle(
        validator: String,
        rewardPeriod: RewardPeriod,
        cycle: Long,
    ): List<TokenReward> =
        query(
            "$CURRENT AND validator = ? AND reward_period = CAST(? AS token_reward.period) " +
                "AND cycle = ? ORDER BY id",
            PostgresHex.bytes(validator),
            rewardPeriod.name,
            cycle,
        )

    open fun findAllById(ids: List<String>): List<TokenReward> =
        if (ids.isEmpty()) emptyList()
        else query("$CURRENT AND id = ANY(?) ORDER BY id", ids.toTypedArray())

    override fun rollbackFrom(blockNumber: Long) {
        jdbc.update("DELETE FROM token_reward.state WHERE block_number >= ?", blockNumber)
        jdbc.update(
            "UPDATE token_reward.state SET superseded_at = NULL WHERE superseded_at >= ?",
            blockNumber,
        )
    }

    override fun truncate() {
        jdbc.execute("TRUNCATE token_reward.state")
    }

    /** The store records [before] once rows are gone and refuses any rollback below it. */
    override fun prune(before: Long): Int =
        jdbc.update("DELETE FROM token_reward.state WHERE superseded_at < ?", before)

    private fun query(sql: String, vararg args: Any): List<TokenReward> =
        jdbc.query(sql, { rs, _ -> TokenRewardRowMapping.read(rs) }, *args)

    companion object {
        private const val CURRENT = "SELECT * FROM token_reward.state WHERE superseded_at IS NULL"
        private val INSERT =
            "INSERT INTO token_reward.state (id, block_number, " +
                TokenRewardRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, ?, " +
                TokenRewardRowMapping.COLUMNS.joinToString {
                    if (it == "reward_period") "CAST(? AS token_reward.period)" else "?"
                } +
                ") ON CONFLICT (id, block_number) DO UPDATE SET " +
                TokenRewardRowMapping.COLUMNS.joinToString { "$it = EXCLUDED.$it" }
    }
}
