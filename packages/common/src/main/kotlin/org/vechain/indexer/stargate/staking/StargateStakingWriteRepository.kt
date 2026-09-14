package org.vechain.indexer.stargate.staking

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresIndexerTables
import org.vechain.indexer.stargate.staking.NftOwnerBalanceRowMapping.TABLE
import org.vechain.indexer.timeseries.TimeFrameTable

/**
 * The `stargate_staking` schema: both series and the owner-balance changelog, rolled back by block.
 */
@Repository
@ConditionalOnPostgres
open class StargateStakingWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    private val vetStaked = TimeFrameTable(jdbc, VetStakedRowMapping)
    private val nftHolders = TimeFrameTable(jdbc, NftHoldersRowMapping)

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(
        vetStaked: List<VetStakedByBlock>,
        nftHolders: List<NftHoldersByBlock>,
        ownerBalances: List<NftOwnerBalance>,
    ) {
        this.vetStaked.save(vetStaked)
        this.nftHolders.save(nftHolders)
        if (ownerBalances.isEmpty()) return
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = ownerBalances.associateBy { it.owner to it.blockNumber }.values.toList()
        jdbc.batchUpdate(INSERT, rows, rows.size) { ps, b -> NftOwnerBalanceRowMapping.bind(ps, b) }
    }

    open fun latestVetStaked(): VetStakedByBlock? = vetStaked.latest()

    open fun latestNftHolders(): NftHoldersByBlock? = nftHolders.latest()

    /** Each of [owners]' newest balance below [blockNumber]; an owner never seen is absent. */
    open fun latestBalancesBefore(owners: Set<String>, blockNumber: Long): List<NftOwnerBalance> =
        jdbc.query(
            "SELECT DISTINCT ON (owner) * FROM $TABLE WHERE owner = ANY(?) AND block_number < ? " +
                "ORDER BY owner, block_number DESC",
            { rs, _ -> NftOwnerBalanceRowMapping.read(rs) },
            owners.map(::bytes).toTypedArray(),
            blockNumber,
        )

    override fun rollbackFrom(blockNumber: Long) {
        vetStaked.rollbackFrom(blockNumber)
        nftHolders.rollbackFrom(blockNumber)
        jdbc.update("DELETE FROM $TABLE WHERE block_number >= ?", blockNumber)
    }

    override fun truncate() {
        vetStaked.truncate()
        nftHolders.truncate()
        jdbc.execute("TRUNCATE $TABLE")
    }

    companion object {
        private val INSERT =
            "INSERT INTO $TABLE (owner, block_number, " +
                NftOwnerBalanceRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, ?, " +
                NftOwnerBalanceRowMapping.COLUMNS.joinToString { "?" } +
                ") ON CONFLICT (owner, block_number) DO UPDATE SET " +
                NftOwnerBalanceRowMapping.COLUMNS.joinToString { "$it = EXCLUDED.$it" }
    }
}
