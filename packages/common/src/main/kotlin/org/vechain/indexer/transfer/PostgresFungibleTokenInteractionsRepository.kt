package org.vechain.indexer.transfer

import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.thor.model.BlockIdentifier

@Profile("transfers")
@Repository
open class PostgresFungibleTokenInteractionsRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val namedJdbcTemplate: NamedParameterJdbcTemplate,
) : FungibleTokenInteractionsRepository {

    private fun tableName(): String = "fungible_token_interactions"

    private fun mapRow(rs: ResultSet): FungibleTokenInteraction {
        return FungibleTokenInteraction(
            id = rs.getString("id"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            contractAddress = rs.getString("contract_address"),
            walletAddress = rs.getString("wallet_address"),
        )
    }

    private fun insertColumns(): String =
        "id, block_id, block_number, block_timestamp, contract_address, wallet_address"

    private fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?"

    private fun insertParams(interaction: FungibleTokenInteraction): Array<Any?> =
        arrayOf(
            interaction.id,
            interaction.blockId,
            interaction.blockNumber,
            interaction.blockTimestamp,
            interaction.contractAddress,
            interaction.walletAddress,
        )

    @Transactional(rollbackFor = [Exception::class])
    override fun saveAll(interactions: List<FungibleTokenInteraction>) {
        if (interactions.isEmpty()) return

        // Use ON CONFLICT DO NOTHING to handle duplicates - first insert wins
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO ${tableName()} (${insertColumns()})
            VALUES (${insertPlaceholders()})
            ON CONFLICT (id) DO NOTHING
            """
                .trimIndent(),
            interactions.map { insertParams(it) },
        )
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun rollback(blockNumber: Long) {
        jdbcTemplate.update("DELETE FROM ${tableName()} WHERE block_number >= ?", blockNumber)
    }

    override fun getLatestBlockIdentifier(): BlockIdentifier? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT block_number, block_id FROM ${tableName()}
                ORDER BY block_number DESC
                LIMIT 1
                """
                    .trimIndent()
            ) { rs, _ ->
                BlockIdentifier(number = rs.getLong("block_number"), id = rs.getString("block_id"))
            }
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    override fun findByWalletAddress(
        walletAddress: String,
        pageable: Pageable,
    ): Slice<FungibleTokenInteraction> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE wallet_address = ?
                ORDER BY block_number DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                walletAddress,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findAllByWalletAddressAndContractAddresses(
        walletAddress: String,
        contractAddresses: List<String>,
        pageable: Pageable,
    ): Slice<FungibleTokenInteraction> {
        if (contractAddresses.isEmpty()) {
            return SliceImpl(emptyList(), pageable, false)
        }

        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            namedJdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE wallet_address = :walletAddress
                AND contract_address IN (:contractAddresses)
                ORDER BY block_number DESC
                LIMIT :limit OFFSET :offset
                """
                    .trimIndent(),
                mapOf(
                    "walletAddress" to walletAddress,
                    "contractAddresses" to contractAddresses,
                    "limit" to limit,
                    "offset" to offset,
                ),
            ) { rs, _ ->
                mapRow(rs)
            }

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }
}
