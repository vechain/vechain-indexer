package org.vechain.indexer.b3tr.navigator

import java.sql.ResultSet
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresIndexerTables

/** Everything one block adds to the schema. */
data class NavigatorUpdate(
    val navigators: List<Navigator> = emptyList(),
    val citizens: List<NavigatorCitizen> = emptyList(),
    val delegationEvents: List<NavigatorDelegationEvent> = emptyList(),
    val fees: List<NavigatorFee> = emptyList(),
) {
    fun isEmpty(): Boolean =
        navigators.isEmpty() && citizens.isEmpty() && delegationEvents.isEmpty() && fees.isEmpty()
}

/** The `b3tr_navigator` schema: navigators, their citizens, delegation events and fees. */
@Repository
@ConditionalOnPostgres
open class NavigatorWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    /** One transaction, so a navigator and its citizens cannot be split by a rollback. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(update: NavigatorUpdate) {
        update.navigators.groupBy { it.blockNumber }.toSortedMap().forEach(::saveNavigators)
        update.citizens.groupBy { it.blockNumber }.toSortedMap().forEach(::saveCitizens)
        update.fees.groupBy { it.blockNumber }.toSortedMap().forEach(::saveFees)
        saveDelegationEvents(update.delegationEvents)
    }

    // `block_number < ?` keeps a replayed block from closing its own rows; upsert does the rest.
    private fun saveNavigators(blockNumber: Long, block: List<Navigator>) {
        val rows = block.associateBy { it.address }.values.toList()
        jdbc.batchUpdate(
            "UPDATE $NAVIGATOR_TABLE SET superseded_at = ? WHERE address = ? " +
                "AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, n ->
            ps.setLong(1, blockNumber)
            ps.setBytes(2, bytes(n.address))
            ps.setLong(3, blockNumber)
        }
        jdbc.batchUpdate(NAVIGATOR_INSERT, rows, rows.size) { ps, n ->
            NavigatorRowMapping.bind(ps, n)
        }
    }

    private fun saveCitizens(blockNumber: Long, block: List<NavigatorCitizen>) {
        val rows = block.associateBy { it.address }.values.toList()
        jdbc.batchUpdate(
            "UPDATE $CITIZEN_TABLE SET superseded_at = ? WHERE address = ? " +
                "AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, c ->
            ps.setLong(1, blockNumber)
            ps.setBytes(2, bytes(c.address))
            ps.setLong(3, blockNumber)
        }
        jdbc.batchUpdate(CITIZEN_INSERT, rows, rows.size) { ps, c ->
            NavigatorCitizenRowMapping.bind(ps, c)
        }
    }

    private fun saveFees(blockNumber: Long, block: List<NavigatorFee>) {
        val rows = block.associateBy { it.navigator to it.roundId }.values.toList()
        jdbc.batchUpdate(
            "UPDATE $FEE_TABLE SET superseded_at = ? WHERE navigator = ? AND round_id = ? " +
                "AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, f ->
            ps.setLong(1, blockNumber)
            ps.setBytes(2, bytes(f.navigator))
            ps.setInt(3, f.roundId)
            ps.setLong(4, blockNumber)
        }
        jdbc.batchUpdate(FEE_INSERT, rows, rows.size) { ps, f ->
            NavigatorFeeRowMapping.bind(ps, f)
        }
    }

    private fun saveDelegationEvents(events: List<NavigatorDelegationEvent>) {
        if (events.isEmpty()) return
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = events.associateBy { it.id }.values.toList()
        jdbc.batchUpdate(DELEGATION_EVENT_INSERT, rows, rows.size) { ps, e ->
            NavigatorDelegationEventRowMapping.bind(ps, e)
        }
    }

    /** The row of every navigator in [addresses] as it stood before [block]. */
    open fun findCurrentNavigators(addresses: Set<String>, block: Long): List<Navigator> =
        if (addresses.isEmpty()) emptyList()
        else
            jdbc.query(
                "SELECT * FROM $NAVIGATOR_TABLE WHERE address = ANY(?) AND $AS_OF",
                { rs, _ -> NavigatorRowMapping.read(rs) },
                addresses.map(::bytes).toTypedArray(),
                block,
                block,
            )

    /** The navigators exiting before [block] whose deadline has passed by it. */
    open fun findExpiredExits(block: Long): List<Navigator> =
        jdbc.query(
            "SELECT * FROM $NAVIGATOR_TABLE WHERE status = 'EXITING' " +
                "AND exit_effective_deadline_block <= ? AND $AS_OF ORDER BY address",
            { rs, _ -> NavigatorRowMapping.read(rs) },
            block,
            block,
            block,
        )

    /** The row of every citizen in [addresses] as it stood before [block]. */
    open fun findCurrentCitizens(addresses: Set<String>, block: Long): List<NavigatorCitizen> =
        if (addresses.isEmpty()) emptyList()
        else
            jdbc.query(
                "SELECT * FROM $CITIZEN_TABLE WHERE address = ANY(?) AND $AS_OF",
                { rs, _ -> NavigatorCitizenRowMapping.read(rs) },
                addresses.map(::bytes).toTypedArray(),
                block,
                block,
            )

    /** The citizens delegating to any of [navigators] before [block]. */
    open fun findActiveCitizens(navigators: Set<String>, block: Long): List<NavigatorCitizen> =
        if (navigators.isEmpty()) emptyList()
        else
            jdbc.query(
                "SELECT * FROM $CITIZEN_TABLE WHERE navigator = ANY(?) AND active AND $AS_OF " +
                    "ORDER BY delegated_at, address",
                { rs, _ -> NavigatorCitizenRowMapping.read(rs) },
                navigators.map(::bytes).toTypedArray(),
                block,
                block,
            )

    /**
     * The row of every (navigator, roundId) pair in [keys] before [block]. The query widens to the
     * cross product of the two halves, whose extra rows the caller simply never looks up.
     */
    open fun findCurrentFees(keys: Set<Pair<String, Int>>, block: Long): List<NavigatorFee> =
        if (keys.isEmpty()) emptyList()
        else
            jdbc.query(
                "SELECT * FROM $FEE_TABLE WHERE navigator = ANY(?) AND round_id = ANY(?) AND $AS_OF",
                { rs: ResultSet, _ -> NavigatorFeeRowMapping.read(rs) },
                keys.map { bytes(it.first) }.distinct().toTypedArray(),
                keys.map { it.second }.distinct().toTypedArray(),
                block,
                block,
            )

    override fun rollbackFrom(blockNumber: Long) {
        TEMPORAL_TABLES.forEach { table ->
            jdbc.update("DELETE FROM $table WHERE block_number >= ?", blockNumber)
            jdbc.update(
                "UPDATE $table SET superseded_at = NULL WHERE superseded_at >= ?",
                blockNumber,
            )
        }
        jdbc.update("DELETE FROM $DELEGATION_EVENT_TABLE WHERE block_number >= ?", blockNumber)
    }

    override fun truncate() {
        jdbc.execute(
            "TRUNCATE $NAVIGATOR_TABLE, $CITIZEN_TABLE, $FEE_TABLE, $DELEGATION_EVENT_TABLE"
        )
    }

    override fun prune(before: Long): Int = TEMPORAL_TABLES.sumOf { table ->
        jdbc.update("DELETE FROM $table WHERE superseded_at < ?", before)
    }

    companion object {
        private const val NAVIGATOR_TABLE = NavigatorRowMapping.TABLE
        private const val CITIZEN_TABLE = NavigatorCitizenRowMapping.TABLE
        private const val FEE_TABLE = NavigatorFeeRowMapping.TABLE
        private const val DELEGATION_EVENT_TABLE = NavigatorDelegationEventRowMapping.TABLE
        private val TEMPORAL_TABLES = listOf(NAVIGATOR_TABLE, CITIZEN_TABLE, FEE_TABLE)

        // Reads are as of the block before, so a replayed block starts where its first run did.
        private const val AS_OF =
            "block_number < ? AND (superseded_at IS NULL OR superseded_at >= ?)"

        private fun upsert(table: String, key: List<String>, columns: List<String>) =
            "INSERT INTO $table (" +
                (key + columns).joinToString() +
                ") VALUES (" +
                (key + columns).joinToString {
                    if (it == "status") "CAST(? AS ${NavigatorRowMapping.STATUS_TYPE})" else "?"
                } +
                ") ON CONFLICT (" +
                key.joinToString() +
                ") DO UPDATE SET " +
                columns.joinToString { "$it = EXCLUDED.$it" }

        private val NAVIGATOR_INSERT =
            upsert(NAVIGATOR_TABLE, listOf("address", "block_number"), NavigatorRowMapping.COLUMNS)

        private val CITIZEN_INSERT =
            upsert(
                CITIZEN_TABLE,
                listOf("address", "block_number"),
                NavigatorCitizenRowMapping.COLUMNS,
            )

        private val FEE_INSERT =
            upsert(
                FEE_TABLE,
                listOf("navigator", "round_id", "block_number"),
                NavigatorFeeRowMapping.COLUMNS,
            )

        private val DELEGATION_EVENT_INSERT =
            "INSERT INTO $DELEGATION_EVENT_TABLE (id, " +
                NavigatorDelegationEventRowMapping.COLUMNS.joinToString() +
                ") VALUES (?" +
                ", ?".repeat(NavigatorDelegationEventRowMapping.COLUMNS.size) +
                ") ON CONFLICT (id) DO NOTHING"
    }
}
