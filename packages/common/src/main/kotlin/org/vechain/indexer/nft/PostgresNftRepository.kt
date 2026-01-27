package org.vechain.indexer.nft

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.postgres.PostgresVersionedRepository

@Profile("nfts")
@Repository
open class PostgresNftRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<IndexedNft>(jdbcTemplate, namedJdbcTemplate, objectMapper),
    NftRepository {

    override fun tableName(): String = "nfts"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        token_id, contract_address, owner, tx_id, is_blacklisted
        """
            .trimIndent()

    override fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"

    override fun insertParams(doc: IndexedNft): Array<Any?> =
        arrayOf(
            doc.id,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.tokenId,
            doc.contractAddress,
            doc.owner,
            doc.txId,
            doc.isBlacklisted,
        )

    override fun mapRow(rs: ResultSet): IndexedNft {
        return IndexedNft(
            id = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            tokenId = rs.getString("token_id"),
            contractAddress = rs.getString("contract_address"),
            owner = rs.getString("owner"),
            txId = rs.getString("tx_id"),
            isBlacklisted = rs.getObject("is_blacklisted") as? Boolean,
        )
    }

    override fun saveAllVersioned(updated: List<IndexedNft>, existing: List<IndexedNft>) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findAllById(ids: List<String>): List<IndexedNft> {
        if (ids.isEmpty()) return emptyList()

        return namedJdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE ${entityIdColumn()} IN (:ids) AND is_current = true
            """
                .trimIndent(),
            mapOf("ids" to ids),
        ) { rs, _ ->
            mapRow(rs)
        }
    }

    override fun findByOwner(
        owner: String,
        excludeCollections: List<String>,
        pageable: Pageable,
    ): Slice<IndexedNft> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            if (excludeCollections.isEmpty()) {
                jdbcTemplate.query(
                    """
                    SELECT * FROM ${tableName()}
                    WHERE owner = ? AND is_current = true
                    AND (is_blacklisted IS NULL OR is_blacklisted = false)
                    ORDER BY block_number DESC, tx_id DESC, entity_id DESC
                    LIMIT ? OFFSET ?
                    """
                        .trimIndent(),
                    { rs, _ -> mapRow(rs) },
                    owner,
                    limit,
                    offset,
                )
            } else {
                namedJdbcTemplate.query(
                    """
                    SELECT * FROM ${tableName()}
                    WHERE owner = :owner AND is_current = true
                    AND contract_address NOT IN (:excludeCollections)
                    AND (is_blacklisted IS NULL OR is_blacklisted = false)
                    ORDER BY block_number DESC, tx_id DESC, entity_id DESC
                    LIMIT :limit OFFSET :offset
                    """
                        .trimIndent(),
                    mapOf(
                        "owner" to owner,
                        "excludeCollections" to excludeCollections,
                        "limit" to limit,
                        "offset" to offset,
                    ),
                ) { rs, _ ->
                    mapRow(rs)
                }
            }

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findByOwnerAndContractAddress(
        owner: String,
        contractAddress: String,
        pageable: Pageable,
    ): Slice<IndexedNft> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE owner = ? AND contract_address = ? AND is_current = true
                AND (is_blacklisted IS NULL OR is_blacklisted = false)
                ORDER BY block_number DESC, tx_id DESC, entity_id DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                owner,
                contractAddress,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findByOwnerAndContractAddressAndTokenId(
        owner: String,
        contractAddress: String,
        tokenId: String,
        pageable: Pageable,
    ): Slice<IndexedNft> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE owner = ? AND contract_address = ? AND token_id = ? AND is_current = true
                AND (is_blacklisted IS NULL OR is_blacklisted = false)
                ORDER BY block_number DESC, tx_id DESC, entity_id DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                owner,
                contractAddress,
                tokenId,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findContractsByNftOwner(
        owner: String,
        excludeCollections: List<String>,
        pageable: Pageable,
    ): Slice<String> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            if (excludeCollections.isEmpty()) {
                jdbcTemplate.queryForList(
                    """
                    SELECT DISTINCT contract_address FROM ${tableName()}
                    WHERE owner = ? AND is_current = true
                    AND (is_blacklisted IS NULL OR is_blacklisted = false)
                    ORDER BY contract_address
                    LIMIT ? OFFSET ?
                    """
                        .trimIndent(),
                    String::class.java,
                    owner,
                    limit,
                    offset,
                )
            } else {
                namedJdbcTemplate.queryForList(
                    """
                    SELECT DISTINCT contract_address FROM ${tableName()}
                    WHERE owner = :owner AND is_current = true
                    AND contract_address NOT IN (:excludeCollections)
                    AND (is_blacklisted IS NULL OR is_blacklisted = false)
                    ORDER BY contract_address
                    LIMIT :limit OFFSET :offset
                    """
                        .trimIndent(),
                    mapOf(
                        "owner" to owner,
                        "excludeCollections" to excludeCollections,
                        "limit" to limit,
                        "offset" to offset,
                    ),
                    String::class.java,
                )
            }

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun blacklist(contractAddresses: List<String>) {
        if (contractAddresses.isEmpty()) return

        namedJdbcTemplate.update(
            """
            UPDATE ${tableName()}
            SET is_blacklisted = true
            WHERE contract_address IN (:addresses)
            """
                .trimIndent(),
            mapOf("addresses" to contractAddresses),
        )
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun whitelist(contractAddresses: List<String>) {
        if (contractAddresses.isEmpty()) return

        namedJdbcTemplate.update(
            """
            UPDATE ${tableName()}
            SET is_blacklisted = false
            WHERE contract_address IN (:addresses)
            """
                .trimIndent(),
            mapOf("addresses" to contractAddresses),
        )
    }
}
