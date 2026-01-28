package org.vechain.indexer.version

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.jdbc.core.JdbcTemplate
import org.vechain.indexer.thor.model.BlockIdentifier
import strikt.api.expect
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNull
import strikt.assertions.isTrue

internal class IndexerVersionServiceTest {
    private lateinit var repo: IndexerVersionRepository
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var service: IndexerVersionService

    @BeforeEach
    fun setUp() {
        repo = mockk(relaxed = true)
        jdbcTemplate = mockk(relaxed = true)
        service = IndexerVersionService(repo, jdbcTemplate)
    }

    @Test
    fun `getStoredIndexerVersion - returns version when found`() {
        val tableName = "test_table"
        every { repo.findByTableName(tableName) } returns
            IndexerVersion(
                indexerName = "TestIndexer",
                tableName = tableName,
                version = 2,
                lastProcessedBlock = null,
            )

        val version = service.getStoredIndexerVersion(tableName)

        expect { that(version).isEqualTo(2) }
    }

    @Test
    fun `getStoredIndexerVersion - returns null when not found`() {
        every { repo.findByTableName("unknown_table") } returns null

        val version = service.getStoredIndexerVersion("unknown_table")

        expect { that(version).isNull() }
    }

    @Test
    fun `getLastProcessedBlock - returns block when found`() {
        val indexerName = "TestIndexer"
        val expectedBlock = BlockIdentifier(123, "0xabc")
        every { repo.findById(indexerName) } returns
            IndexerVersion(
                indexerName = indexerName,
                tableName = "test_table",
                version = 1,
                lastProcessedBlock = expectedBlock,
            )

        val block = service.getLastProcessedBlock(indexerName)

        expect { that(block).isEqualTo(expectedBlock) }
    }

    @Test
    fun `getLastProcessedBlock - returns null when not found`() {
        every { repo.findById("unknown") } returns null

        val block = service.getLastProcessedBlock("unknown")

        expect { that(block).isNull() }
    }

    @Test
    fun `updateIndexerVersion - creates new version when not exists`() {
        val indexerName = "TestIndexer"
        val tableName = "test_table"
        every { repo.findById(indexerName) } returns null

        val saved = slot<IndexerVersion>()
        every { repo.save(capture(saved)) } answers { firstArg() }

        service.updateIndexerVersion(indexerName, tableName, 2)

        expect {
            that(saved.captured.indexerName).isEqualTo(indexerName)
            that(saved.captured.tableName).isEqualTo(tableName)
            that(saved.captured.version).isEqualTo(2)
            that(saved.captured.lastProcessedBlock).isNull()
        }
    }

    @Test
    fun `updateIndexerVersion - updates existing version and clears block`() {
        val indexerName = "TestIndexer"
        val tableName = "test_table"
        every { repo.findById(indexerName) } returns
            IndexerVersion(
                indexerName = indexerName,
                tableName = tableName,
                version = 1,
                lastProcessedBlock = BlockIdentifier(123, "0xabc"),
            )

        val saved = slot<IndexerVersion>()
        every { repo.save(capture(saved)) } answers { firstArg() }

        service.updateIndexerVersion(indexerName, tableName, 2)

        expect {
            that(saved.captured.indexerName).isEqualTo(indexerName)
            that(saved.captured.tableName).isEqualTo(tableName)
            that(saved.captured.version).isEqualTo(2)
            that(saved.captured.lastProcessedBlock).isNull()
        }
    }

    @Test
    fun `updateLastSafeSyncedBlock - updates block when indexer exists`() {
        val indexerName = "TestIndexer"
        val newBlock = BlockIdentifier(456, "0xdef")
        every { repo.findById(indexerName) } returns
            IndexerVersion(
                indexerName = indexerName,
                tableName = "test_table",
                version = 1,
                lastProcessedBlock = BlockIdentifier(123, "0xabc"),
            )

        val saved = slot<IndexerVersion>()
        every { repo.save(capture(saved)) } answers { firstArg() }

        service.updateLastSafeSyncedBlock(indexerName, newBlock)

        expect { that(saved.captured.lastProcessedBlock).isEqualTo(newBlock) }
    }

    @Test
    fun `updateLastSafeSyncedBlock - does nothing when block is null`() {
        service.updateLastSafeSyncedBlock("TestIndexer", null)

        verify(exactly = 0) { repo.findById(any()) }
        verify(exactly = 0) { repo.save(any()) }
    }

    @Test
    fun `updateLastSafeSyncedBlock - does nothing when indexer not found`() {
        every { repo.findById("unknown") } returns null

        service.updateLastSafeSyncedBlock("unknown", BlockIdentifier(123, "0xabc"))

        verify(exactly = 0) { repo.save(any()) }
    }

    @Test
    fun `ensureTableExists - creates table when no version record exists`() {
        val indexerName = "TestIndexer"
        val tableName = "test_table"
        val schemaResource = "db/tables/test_table.sql"

        every { repo.findByTableName(tableName) } returns null
        every { repo.findById(indexerName) } returns null

        val saved = slot<IndexerVersion>()
        every { repo.save(capture(saved)) } answers { firstArg() }

        // Mock readSchemaResource
        val spyService =
            object : IndexerVersionService(repo, jdbcTemplate) {
                override fun readSchemaResource(schemaResource: String): String {
                    return "CREATE TABLE IF NOT EXISTS test_table (id TEXT PRIMARY KEY);"
                }
            }

        every { jdbcTemplate.execute(any<String>()) } just runs

        val result = spyService.ensureTableExists(indexerName, tableName, schemaResource, 1)

        expect {
            that(result).isTrue()
            that(saved.captured.indexerName).isEqualTo(indexerName)
            that(saved.captured.tableName).isEqualTo(tableName)
            that(saved.captured.version).isEqualTo(1)
        }
        verify(exactly = 1) {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS test_table (id TEXT PRIMARY KEY)")
        }
    }

    @Test
    fun `ensureTableExists - recreates table when version is lower`() {
        val indexerName = "TestIndexer"
        val tableName = "test_table"
        val schemaResource = "db/tables/test_table.sql"

        every { repo.findByTableName(tableName) } returns
            IndexerVersion(
                indexerName = indexerName,
                tableName = tableName,
                version = 1,
                lastProcessedBlock = BlockIdentifier(123, "0xabc"),
            )
        every { repo.findById(indexerName) } returns
            IndexerVersion(
                indexerName = indexerName,
                tableName = tableName,
                version = 1,
                lastProcessedBlock = BlockIdentifier(123, "0xabc"),
            )

        val saved = slot<IndexerVersion>()
        every { repo.save(capture(saved)) } answers { firstArg() }

        val spyService =
            object : IndexerVersionService(repo, jdbcTemplate) {
                override fun readSchemaResource(schemaResource: String): String {
                    return "CREATE TABLE IF NOT EXISTS test_table (id TEXT PRIMARY KEY);"
                }
            }

        every { jdbcTemplate.execute(any<String>()) } just runs

        val result = spyService.ensureTableExists(indexerName, tableName, schemaResource, 2)

        expect {
            that(result).isTrue()
            that(saved.captured.version).isEqualTo(2)
            that(saved.captured.lastProcessedBlock).isNull()
        }
        verify(exactly = 1) { jdbcTemplate.execute("DROP TABLE IF EXISTS \"test_table\" CASCADE") }
    }

    @Test
    fun `ensureTableExists - does nothing when version is equal`() {
        val indexerName = "TestIndexer"
        val tableName = "test_table"
        val schemaResource = "db/tables/test_table.sql"

        every { repo.findByTableName(tableName) } returns
            IndexerVersion(
                indexerName = indexerName,
                tableName = tableName,
                version = 2,
                lastProcessedBlock = null,
            )

        val result = service.ensureTableExists(indexerName, tableName, schemaResource, 2)

        expect { that(result).isFalse() }
        verify(exactly = 0) { repo.save(any()) }
        verify(exactly = 0) { jdbcTemplate.execute(any<String>()) }
    }

    @Test
    fun `ensureTableExists - does nothing when stored version is higher`() {
        val indexerName = "TestIndexer"
        val tableName = "test_table"
        val schemaResource = "db/tables/test_table.sql"

        every { repo.findByTableName(tableName) } returns
            IndexerVersion(
                indexerName = indexerName,
                tableName = tableName,
                version = 3,
                lastProcessedBlock = null,
            )

        val result = service.ensureTableExists(indexerName, tableName, schemaResource, 2)

        expect { that(result).isFalse() }
        verify(exactly = 0) { repo.save(any()) }
        verify(exactly = 0) { jdbcTemplate.execute(any<String>()) }
    }

    @Test
    fun `ensureTableExists - throws exception on error`() {
        val indexerName = "TestIndexer"
        val tableName = "test_table"
        val schemaResource = "db/tables/test_table.sql"

        every { repo.findByTableName(tableName) } throws RuntimeException("DB error")

        assertThrows<RuntimeException> {
            service.ensureTableExists(indexerName, tableName, schemaResource, 1)
        }
    }

    @Test
    fun `createTable - executes schema statements`() {
        val schemaResource = "db/tables/test_table.sql"

        val spyService =
            object : IndexerVersionService(repo, jdbcTemplate) {
                override fun readSchemaResource(schemaResource: String): String {
                    return """
                        -- Test table
                        CREATE TABLE IF NOT EXISTS test_table (id TEXT PRIMARY KEY);
                        CREATE INDEX IF NOT EXISTS idx_test ON test_table (id);
                    """
                        .trimIndent()
                }
            }

        every { jdbcTemplate.execute(any<String>()) } just runs

        spyService.createTable(schemaResource)

        verify(exactly = 1) {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS test_table (id TEXT PRIMARY KEY)")
        }
        verify(exactly = 1) {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_test ON test_table (id)")
        }
    }

    @Test
    fun `dropAndRecreateTable - drops and recreates table`() {
        val tableName = "test_table"
        val schemaResource = "db/tables/test_table.sql"

        val spyService =
            object : IndexerVersionService(repo, jdbcTemplate) {
                override fun readSchemaResource(schemaResource: String): String {
                    return "CREATE TABLE IF NOT EXISTS test_table (id TEXT PRIMARY KEY);"
                }
            }

        every { jdbcTemplate.execute(any<String>()) } just runs

        spyService.dropAndRecreateTable(tableName, schemaResource)

        verify(exactly = 1) { jdbcTemplate.execute("DROP TABLE IF EXISTS \"test_table\" CASCADE") }
        verify(exactly = 1) {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS test_table (id TEXT PRIMARY KEY)")
        }
    }
}
