package org.vechain.indexer.contracts.repository

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.contracts.Contract
import org.vechain.indexer.postgres.PostgresVersionedRepository

@Profile("contracts", "contract")
@Repository
open class PostgresContractRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<Contract>(jdbcTemplate, namedJdbcTemplate, objectMapper),
    ContractRepository {

    override fun tableName(): String = "contracts"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        created_on, deployment_tx_id, deployment_clause_index, master,
        is_erc20, is_erc721, is_erc1155
        """
            .trimIndent()

    override fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"

    override fun insertParams(doc: Contract): Array<Any?> =
        arrayOf(
            doc.address,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.createdOn,
            doc.deploymentTxId,
            doc.deploymentClauseIndex,
            doc.master,
            doc.isErc20,
            doc.isErc721,
            doc.isErc1155,
        )

    override fun mapRow(rs: ResultSet): Contract {
        return Contract(
            address = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            createdOn = rs.getLong("created_on"),
            deploymentTxId = rs.getString("deployment_tx_id"),
            deploymentClauseIndex = rs.getLong("deployment_clause_index"),
            master = rs.getString("master"),
            isErc20 = rs.getObject("is_erc20") as? Boolean,
            isErc721 = rs.getObject("is_erc721") as? Boolean,
            isErc1155 = rs.getObject("is_erc1155") as? Boolean,
        )
    }

    override fun saveAllVersioned(updated: List<Contract>, existing: List<Contract>) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findById(id: String): Contract? {
        return findCurrentByEntityId(id)
    }

    override fun findByMaster(master: String, pageable: Pageable): Slice<Contract> {
        val limit = pageable.pageSize + 1 // Fetch one extra to detect hasNext
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE master = ? AND is_current = true
                ORDER BY created_on DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                master,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }
}
