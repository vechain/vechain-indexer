package org.vechain.indexer.version

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.thor.model.BlockIdentifier
import strikt.api.expect
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

internal class IndexerVersionServiceTest {
    private lateinit var repo: IndexerVersionRepository

    private lateinit var service: IndexerVersionService

    @BeforeEach
    fun setUp() {
        repo = mockk(relaxed = true)
        service = IndexerVersionService(repo)
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
}
