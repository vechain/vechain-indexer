package org.vechain.indexer.postgres

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.sql.ResultSet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.vechain.indexer.VersionedDocument
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class PostgresVersionedRepositoryTest {

    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var namedJdbcTemplate: NamedParameterJdbcTemplate
    private lateinit var objectMapper: ObjectMapper
    private lateinit var repository: TestVersionedRepository

    @BeforeEach
    fun setup() {
        jdbcTemplate = mockk(relaxed = true)
        namedJdbcTemplate = mockk(relaxed = true)
        objectMapper = ObjectMapper()
        repository = TestVersionedRepository(jdbcTemplate, namedJdbcTemplate, objectMapper)
    }

    @Test
    fun `insertParamsForExisting returns params with is_current set to false`() {
        val doc =
            TestDocument(
                id = "test-entity-1",
                version = 3,
                blockId = "0xblock123",
                blockNumber = 100L,
                blockTimestamp = 1234567890L,
                data = "test-data",
            )

        val params = repository.insertParams(doc)
        val paramsForExisting = repository.insertParamsForExisting(doc)

        // Verify insertParams returns is_current=true at index 2
        expectThat(params[2]).isEqualTo(true)

        // Verify insertParamsForExisting returns is_current=false at index 2
        expectThat(paramsForExisting[2]).isEqualTo(false)

        // Verify other params are unchanged
        expectThat(paramsForExisting[0]).isEqualTo(params[0]) // entity_id
        expectThat(paramsForExisting[1]).isEqualTo(params[1]) // version
        expectThat(paramsForExisting[3]).isEqualTo(params[3]) // block_id
        expectThat(paramsForExisting[4]).isEqualTo(params[4]) // block_number
        expectThat(paramsForExisting[5]).isEqualTo(params[5]) // block_timestamp
        expectThat(paramsForExisting[6]).isEqualTo(params[6]) // data
    }

    @Test
    fun `saveAllVersioned inserts existing records with is_current false`() {
        val existingDoc =
            TestDocument(
                id = "test-entity-1",
                version = 1,
                blockId = "0xblock100",
                blockNumber = 100L,
                blockTimestamp = 1000L,
                data = "v1-data",
            )

        val sqlSlot = slot<String>()
        val paramsSlot = slot<List<Array<Any?>>>()
        every { jdbcTemplate.batchUpdate(capture(sqlSlot), capture(paramsSlot)) } returns
            intArrayOf(1)

        repository.saveAllVersioned(updated = emptyList(), existing = listOf(existingDoc))

        verify(exactly = 1) { jdbcTemplate.batchUpdate(any<String>(), any<List<Array<Any?>>>()) }

        // Verify SQL contains ON CONFLICT clause
        expectThat(sqlSlot.captured.contains("ON CONFLICT")).isTrue()
        expectThat(sqlSlot.captured.contains("is_current = false")).isTrue()

        // Verify params have is_current=false at index 2
        val capturedParams = paramsSlot.captured
        expectThat(capturedParams.size).isEqualTo(1)
        expectThat(capturedParams[0][2]).isEqualTo(false)
    }

    @Test
    fun `saveAllVersioned inserts updated records with is_current true`() {
        val updatedDoc =
            TestDocument(
                id = "test-entity-1",
                version = 2,
                blockId = "0xblock101",
                blockNumber = 101L,
                blockTimestamp = 1001L,
                data = "v2-data",
            )

        val sqlSlot = slot<String>()
        val paramsSlot = slot<List<Array<Any?>>>()
        every { jdbcTemplate.batchUpdate(capture(sqlSlot), capture(paramsSlot)) } returns
            intArrayOf(1)

        repository.saveAllVersioned(updated = listOf(updatedDoc), existing = emptyList())

        verify(exactly = 1) { jdbcTemplate.batchUpdate(any<String>(), any<List<Array<Any?>>>()) }

        // Verify SQL contains ON CONFLICT clause
        expectThat(sqlSlot.captured.contains("ON CONFLICT")).isTrue()
        expectThat(sqlSlot.captured.contains("is_current = true")).isTrue()

        // Verify params have is_current=true at index 2
        val capturedParams = paramsSlot.captured
        expectThat(capturedParams.size).isEqualTo(1)
        expectThat(capturedParams[0][2]).isEqualTo(true)
    }

    @Test
    fun `saveAllVersioned inserts both existing and updated records`() {
        val existingDoc =
            TestDocument(
                id = "test-entity-1",
                version = 1,
                blockId = "0xblock100",
                blockNumber = 100L,
                blockTimestamp = 1000L,
                data = "v1-data",
            )
        val updatedDoc =
            TestDocument(
                id = "test-entity-1",
                version = 2,
                blockId = "0xblock101",
                blockNumber = 101L,
                blockTimestamp = 1001L,
                data = "v2-data",
            )

        val sqlSlots = mutableListOf<String>()
        val paramsSlots = mutableListOf<List<Array<Any?>>>()
        every { jdbcTemplate.batchUpdate(capture(sqlSlots), capture(paramsSlots)) } returns
            intArrayOf(1)

        repository.saveAllVersioned(updated = listOf(updatedDoc), existing = listOf(existingDoc))

        // Should have 2 batch update calls - one for existing, one for updated
        verify(exactly = 2) { jdbcTemplate.batchUpdate(any<String>(), any<List<Array<Any?>>>()) }

        // First call should be for existing (is_current = false)
        expectThat(sqlSlots[0].contains("is_current = false")).isTrue()
        expectThat(paramsSlots[0][0][2]).isEqualTo(false)

        // Second call should be for updated (is_current = true)
        expectThat(sqlSlots[1].contains("is_current = true")).isTrue()
        expectThat(paramsSlots[1][0][2]).isEqualTo(true)
    }

    @Test
    fun `saveAllVersioned does nothing when both lists are empty`() {
        repository.saveAllVersioned(updated = emptyList(), existing = emptyList())

        verify(exactly = 0) { jdbcTemplate.batchUpdate(any<String>(), any<List<Array<Any?>>>()) }
    }

    @Test
    fun `saveAllVersioned persists intermediate versions for multi-block batch`() {
        // Simulate a batch processing scenario:
        // Block 100: entity changes from v1 to v2
        // Block 101: entity changes from v2 to v3
        // Block 102: entity changes from v3 to v4
        //
        // existing = [v1, v2, v3] (intermediate versions)
        // updated = [v4] (final version)

        val v1 = TestDocument("entity-1", 1, "0xblock099", 99L, 990L, "v1-data")
        val v2 = TestDocument("entity-1", 2, "0xblock100", 100L, 1000L, "v2-data")
        val v3 = TestDocument("entity-1", 3, "0xblock101", 101L, 1010L, "v3-data")
        val v4 = TestDocument("entity-1", 4, "0xblock102", 102L, 1020L, "v4-data")

        val sqlSlots = mutableListOf<String>()
        val paramsSlots = mutableListOf<List<Array<Any?>>>()
        every { jdbcTemplate.batchUpdate(capture(sqlSlots), capture(paramsSlots)) } returns
            intArrayOf(1, 1, 1, 1)

        repository.saveAllVersioned(updated = listOf(v4), existing = listOf(v1, v2, v3))

        // Should have 2 batch update calls
        verify(exactly = 2) { jdbcTemplate.batchUpdate(any<String>(), any<List<Array<Any?>>>()) }

        // First call should insert ALL existing versions (v1, v2, v3) with is_current=false
        expectThat(paramsSlots[0].size).isEqualTo(3)
        paramsSlots[0].forEach { params -> expectThat(params[2] as Boolean).isFalse() }

        // Second call should insert updated version (v4) with is_current=true
        expectThat(paramsSlots[1].size).isEqualTo(1)
        expectThat(paramsSlots[1][0][2] as Boolean).isTrue()
    }

    // Test implementation of PostgresVersionedRepository
    private class TestVersionedRepository(
        jdbcTemplate: JdbcTemplate,
        namedJdbcTemplate: NamedParameterJdbcTemplate,
        objectMapper: ObjectMapper,
    ) : PostgresVersionedRepository<TestDocument>(jdbcTemplate, namedJdbcTemplate, objectMapper) {

        override fun tableName(): String = "test_documents"

        override fun entityIdColumn(): String = "entity_id"

        override fun insertColumns(): String =
            "entity_id, version, is_current, block_id, block_number, block_timestamp, data"

        override fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?"

        override fun insertParams(doc: TestDocument): Array<Any?> =
            arrayOf(
                doc.id,
                doc.version,
                true, // is_current
                doc.blockId,
                doc.blockNumber,
                doc.blockTimestamp,
                doc.data,
            )

        override fun mapRow(rs: ResultSet): TestDocument {
            return TestDocument(
                id = rs.getString("entity_id"),
                version = rs.getInt("version"),
                blockId = rs.getString("block_id"),
                blockNumber = rs.getLong("block_number"),
                blockTimestamp = rs.getLong("block_timestamp"),
                data = rs.getString("data"),
            )
        }
    }

    // Test document implementation
    private data class TestDocument(
        val id: String,
        override val version: Int,
        override val blockId: String,
        override val blockNumber: Long,
        override val blockTimestamp: Long,
        val data: String,
    ) : VersionedDocument {
        override fun getDocumentId(): String = id
    }
}
