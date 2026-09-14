package org.vechain.indexer.stargate.vetDelegated

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresIndexerTables
import org.vechain.indexer.timeseries.TimeFrameTable

/** `vet_delegated.total_by_block`: one row per changed block, rollback by block. */
@Repository
@ConditionalOnPostgres
open class VetDelegatedWriteRepository(@Qualifier("postgresJdbcTemplate") jdbc: JdbcTemplate) :
    PostgresIndexerTables {

    private val series = TimeFrameTable(jdbc, VetDelegatedRowMapping)

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(records: List<VetDelegatedByBlock>) = series.save(records)

    /** The newest row, from which the service resumes its rollover totals. */
    open fun latest(): VetDelegatedByBlock? = series.latest()

    override fun rollbackFrom(blockNumber: Long) = series.rollbackFrom(blockNumber)

    override fun truncate() = series.truncate()
}
