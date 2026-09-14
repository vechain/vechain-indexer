package org.vechain.indexer.stargate.vthoGenerated

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresIndexerTables
import org.vechain.indexer.timeseries.TimeFrameTable

/** `stargate_vtho_generated.total_by_block`: one row per issuing block, rollback by block. */
@Repository
@ConditionalOnPostgres
open class VthoGeneratedWriteRepository(@Qualifier("postgresJdbcTemplate") jdbc: JdbcTemplate) :
    PostgresIndexerTables {

    private val series = TimeFrameTable(jdbc, VthoGeneratedRowMapping)

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(records: List<VthoGeneratedByBlock>) = series.save(records)

    /** The newest row, from which the service resumes its running total. */
    open fun latest(): VthoGeneratedByBlock? = series.latest()

    override fun rollbackFrom(blockNumber: Long) = series.rollbackFrom(blockNumber)

    override fun truncate() = series.truncate()
}
