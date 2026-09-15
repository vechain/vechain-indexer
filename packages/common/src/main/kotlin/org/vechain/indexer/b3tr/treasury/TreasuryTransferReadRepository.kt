package org.vechain.indexer.b3tr.treasury

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.b3tr.treasury.TreasuryTransferRowMapping.TABLE
import org.vechain.indexer.config.postgres.ConditionalOnPostgres

/** The page behind `/b3tr/treasury/transfers`. */
@Repository
@ConditionalOnPostgres
open class TreasuryTransferReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {

    open fun find(
        category: TreasuryTransferCategory?,
        after: Long?,
        before: Long?,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<TreasuryTransfer> {
        val filters = mutableListOf<String>()
        val args = mutableListOf<Any>()
        if (category != null) {
            filters += "category = CAST(? AS b3tr_treasury.category)"
            args += category.name
        }
        if (after != null) {
            filters += "block_timestamp >= ?"
            args += after
        }
        if (before != null) {
            filters += "block_timestamp <= ?"
            args += before
        }
        val where = if (filters.isEmpty()) "" else "WHERE " + filters.joinToString(" AND ")
        val order = direction.name
        return jdbc.query(
            "SELECT * FROM $TABLE $where " +
                "ORDER BY block_timestamp $order, tx_id $order, id $order OFFSET ? LIMIT ?",
            { rs, _ -> TreasuryTransferRowMapping.read(rs) },
            *(args + offset + limit).toTypedArray(),
        )
    }

    open fun latestBlockNumber(): Long =
        jdbc.queryForObject("SELECT coalesce(max(block_number), 0) FROM $TABLE", Long::class.java)!!
}
