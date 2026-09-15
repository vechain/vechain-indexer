package org.vechain.indexer.contracts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.contracts.ContractRowMapping.TABLE
import org.vechain.indexer.postgres.PostgresHex.bytes

/** The API's reads of the current state of a contract. */
@Repository
@ConditionalOnPostgres
open class ContractReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {

    open fun findByAddress(address: String): Contract? =
        jdbc
            .query(
                "SELECT * FROM $TABLE WHERE address = ? AND superseded_at IS NULL",
                { rs, _ -> ContractRowMapping.read(rs) },
                bytes(address),
            )
            .firstOrNull()

    open fun findByMaster(
        master: String,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<Contract> =
        jdbc.query(
            "SELECT * FROM $TABLE WHERE master = ? AND superseded_at IS NULL " +
                "ORDER BY created_on ${direction.name}, address ${direction.name} " +
                "OFFSET ? LIMIT ?",
            { rs, _ -> ContractRowMapping.read(rs) },
            bytes(master),
            offset,
            limit,
        )
}
