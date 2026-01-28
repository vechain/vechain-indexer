package org.vechain.indexer.version

import java.sql.ResultSet
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.thor.model.BlockIdentifier

@Repository
open class PostgresIndexerVersionRepository(private val jdbcTemplate: JdbcTemplate) :
    IndexerVersionRepository {

    override fun findById(indexerName: String): IndexerVersion? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT indexer_name, table_name, version, last_processed_block_id, 
                       last_processed_block_number, updated_at
                FROM indexer_versions
                WHERE indexer_name = ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                indexerName,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    override fun findByTableName(tableName: String): IndexerVersion? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT indexer_name, table_name, version, last_processed_block_id, 
                       last_processed_block_number, updated_at
                FROM indexer_versions
                WHERE table_name = ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                tableName,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    override fun save(indexerVersion: IndexerVersion): IndexerVersion {
        jdbcTemplate.update(
            """
            INSERT INTO indexer_versions (indexer_name, table_name, version, 
                                          last_processed_block_id, last_processed_block_number, updated_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (indexer_name) DO UPDATE SET
                table_name = EXCLUDED.table_name,
                version = EXCLUDED.version,
                last_processed_block_id = EXCLUDED.last_processed_block_id,
                last_processed_block_number = EXCLUDED.last_processed_block_number,
                updated_at = CURRENT_TIMESTAMP
            """
                .trimIndent(),
            indexerVersion.indexerName,
            indexerVersion.tableName,
            indexerVersion.version,
            indexerVersion.lastProcessedBlock?.id,
            indexerVersion.lastProcessedBlock?.number,
        )
        return findById(indexerVersion.indexerName)!!
    }

    private fun mapRow(rs: ResultSet): IndexerVersion {
        val blockId = rs.getString("last_processed_block_id")
        val blockNumber = rs.getLong("last_processed_block_number")
        val lastProcessedBlock =
            if (blockId != null && !rs.wasNull()) {
                BlockIdentifier(number = blockNumber, id = blockId)
            } else {
                null
            }

        return IndexerVersion(
            indexerName = rs.getString("indexer_name"),
            tableName = rs.getString("table_name"),
            version = rs.getInt("version"),
            updatedAt = rs.getTimestamp("updated_at")?.toLocalDateTime(),
            lastProcessedBlock = lastProcessedBlock,
        )
    }
}
