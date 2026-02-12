package org.vechain.indexer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Minimal [VersionedDocument] for testing. */
private data class TestDocument(
    override val version: Int,
    override val blockId: String = "block-0",
    override val blockNumber: Long = 0L,
    override val blockTimestamp: Long = 0L,
    val id: String = "doc1",
    val value: String = "",
) : VersionedDocument {
    override fun getDocumentId(): String = id
}

internal class VersionedDocumentAccumulatorTest {

    private val db = mutableMapOf<String, TestDocument>()
    private lateinit var accumulator: VersionedDocumentAccumulator<TestDocument>

    @BeforeEach
    fun setUp() {
        db.clear()
        accumulator = VersionedDocumentAccumulator(findById = { db[it] })
    }

    // ===== Basic operations =====

    @Nested
    inner class BasicOperations {
        @Test
        fun `empty accumulator returns empty lists`() {
            val (updated, archived) = accumulator.results()
            assertEquals(emptyList<TestDocument>(), updated)
            assertEquals(emptyList<TestDocument>(), archived)
        }

        @Test
        fun `new record appears in updated with nothing archived`() {
            accumulator.startBlock()
            val (existing, nextVersion) = accumulator.resolve("doc1")

            assertNull(existing)
            assertEquals(1, nextVersion)

            val doc = TestDocument(version = nextVersion, id = "doc1", value = "created")
            accumulator.put("doc1", existing, doc)

            val (updated, archived) = accumulator.results()
            assertEquals(1, updated.size)
            assertEquals(doc, updated.first())
            assertEquals(0, archived.size)
        }

        @Test
        fun `existing record updated causes old version to be archived`() {
            val original = TestDocument(version = 1, id = "doc1", value = "original")
            db["doc1"] = original

            accumulator.startBlock()
            val (existing, nextVersion) = accumulator.resolve("doc1")

            assertEquals(original, existing)
            assertEquals(2, nextVersion)

            val updated = existing!!.copy(version = nextVersion, value = "updated")
            accumulator.put("doc1", existing, updated)

            val (updatedList, archivedList) = accumulator.results()
            assertEquals(1, updatedList.size)
            assertEquals(updated, updatedList.first())
            assertEquals(1, archivedList.size)
            assertEquals(original, archivedList.first())
        }
    }

    // ===== Per-block version tracking (the core fix) =====

    @Nested
    inner class PerBlockVersionTracking {
        @Test
        fun `same record updated twice in same block produces only ONE archive entry`() {
            val original = TestDocument(version = 1, id = "doc1", value = "v1")
            db["doc1"] = original

            accumulator.startBlock()

            // First update
            val (existing1, nextVersion1) = accumulator.resolve("doc1")
            assertEquals(2, nextVersion1)
            val v2 = existing1!!.copy(version = nextVersion1, value = "v2")
            accumulator.put("doc1", existing1, v2)

            // Second update in SAME block
            val (existing2, nextVersion2) = accumulator.resolve("doc1")
            assertEquals(v2, existing2) // cache returns v2
            assertEquals(2, nextVersion2) // no increment — already archived this block

            val v2updated = existing2!!.copy(version = nextVersion2, value = "v2-updated")
            accumulator.put("doc1", existing2, v2updated)

            val (updated, archived) = accumulator.results()
            assertEquals(1, updated.size)
            assertEquals("v2-updated", updated.first().value)
            assertEquals(2, updated.first().version)
            // Only the pre-block state (v1) is archived, NOT the intermediate v2
            assertEquals(1, archived.size)
            assertEquals(original, archived.first())
        }

        @Test
        fun `same record updated three times in same block still only ONE archive entry`() {
            val original = TestDocument(version = 1, id = "doc1", value = "v1")
            db["doc1"] = original

            accumulator.startBlock()

            // First update
            val (e1, nv1) = accumulator.resolve("doc1")
            assertEquals(2, nv1)
            accumulator.put("doc1", e1, e1!!.copy(version = nv1, value = "after-first"))

            // Second update
            val (e2, nv2) = accumulator.resolve("doc1")
            assertEquals(2, nv2) // no increment
            accumulator.put("doc1", e2, e2!!.copy(version = nv2, value = "after-second"))

            // Third update
            val (e3, nv3) = accumulator.resolve("doc1")
            assertEquals(2, nv3) // still no increment
            accumulator.put("doc1", e3, e3!!.copy(version = nv3, value = "after-third"))

            val (updated, archived) = accumulator.results()
            assertEquals(1, updated.size)
            assertEquals("after-third", updated.first().value)
            assertEquals(2, updated.first().version)
            assertEquals(1, archived.size)
            assertEquals(original, archived.first())
        }

        @Test
        fun `same record updated across two blocks produces TWO archive entries`() {
            val original = TestDocument(version = 1, id = "doc1", value = "v1")
            db["doc1"] = original

            // Block 1
            accumulator.startBlock()
            val (e1, nv1) = accumulator.resolve("doc1")
            assertEquals(2, nv1)
            val v2 = e1!!.copy(version = nv1, value = "v2", blockNumber = 1L)
            accumulator.put("doc1", e1, v2)

            // Block 2
            accumulator.startBlock()
            val (e2, nv2) = accumulator.resolve("doc1")
            assertEquals(v2, e2) // cache returns v2
            assertEquals(3, nv2) // increments again because new block
            val v3 = e2!!.copy(version = nv2, value = "v3", blockNumber = 2L)
            accumulator.put("doc1", e2, v3)

            val (updated, archived) = accumulator.results()
            assertEquals(1, updated.size)
            assertEquals(v3, updated.first())
            assertEquals(2, archived.size)
            // Both v1 and v2 should be archived
            val archivedVersions = archived.map { it.version }.toSet()
            assertEquals(setOf(1, 2), archivedVersions)
        }

        @Test
        fun `startBlock resets per-block tracking for second block`() {
            val original = TestDocument(version = 1, id = "doc1", value = "v1")
            db["doc1"] = original

            // Block 1: two updates
            accumulator.startBlock()
            val (e1a, nv1a) = accumulator.resolve("doc1")
            val v2 = e1a!!.copy(version = nv1a, value = "v2")
            accumulator.put("doc1", e1a, v2)

            val (e1b, nv1b) = accumulator.resolve("doc1")
            assertEquals(2, nv1b) // no increment, same block
            val v2b = e1b!!.copy(version = nv1b, value = "v2b")
            accumulator.put("doc1", e1b, v2b)

            // Block 2: one update — should archive v2b
            accumulator.startBlock()
            val (e2, nv2) = accumulator.resolve("doc1")
            assertEquals(v2b, e2)
            assertEquals(3, nv2) // increment because new block
            val v3 = e2!!.copy(version = nv2, value = "v3")
            accumulator.put("doc1", e2, v3)

            val (updated, archived) = accumulator.results()
            assertEquals(1, updated.size)
            assertEquals(v3, updated.first())
            assertEquals(2, archived.size)
        }
    }

    // ===== Version invariant =====

    @Nested
    inner class VersionInvariant {
        @Test
        fun `updated version equals max archive version plus one when archives exist`() {
            val original = TestDocument(version = 1, id = "doc1")
            db["doc1"] = original

            // Update across 3 blocks
            repeat(3) {
                accumulator.startBlock()
                val (existing, nextVersion) = accumulator.resolve("doc1")
                accumulator.put("doc1", existing, existing!!.copy(version = nextVersion))
            }

            val (updated, archived) = accumulator.results()
            val maxArchiveVersion = archived.maxOf { it.version }
            assertEquals(maxArchiveVersion + 1, updated.first().version)
        }

        @Test
        fun `new record created then updated in same block archives the creation`() {
            // No DB record exists
            accumulator.startBlock()

            // First event creates the record
            val (e1, nv1) = accumulator.resolve("doc1")
            assertNull(e1)
            assertEquals(1, nv1)
            val v1 = TestDocument(version = nv1, id = "doc1", value = "created")
            accumulator.put("doc1", e1, v1)

            // Second event in SAME block updates it
            val (e2, nv2) = accumulator.resolve("doc1")
            assertEquals(v1, e2) // cache hit
            // v1 was put with existing=null, so nothing was archived yet.
            // This is the first time we see a non-null existing for this recordId
            // in this block, so it WILL archive v1 and increment.
            assertEquals(2, nv2)
            val v2 = e2!!.copy(version = nv2, value = "updated")
            accumulator.put("doc1", e2, v2)

            val (updated, archived) = accumulator.results()
            assertEquals(1, updated.size)
            assertEquals(v2, updated.first())
            // The v1 state that was replaced should be archived
            assertEquals(1, archived.size)
            assertEquals(v1, archived.first())
        }
    }

    // ===== Cache behavior =====

    @Nested
    inner class CacheBehavior {
        @Test
        fun `resolve returns cached version after put, not from DB`() {
            val dbDoc = TestDocument(version = 1, id = "doc1", value = "db-version")
            db["doc1"] = dbDoc

            accumulator.startBlock()
            val (e1, _) = accumulator.resolve("doc1")
            val cached = e1!!.copy(version = 2, value = "cached-version")
            accumulator.put("doc1", e1, cached)

            // Resolve again — should return cached, not DB
            val (e2, _) = accumulator.resolve("doc1")
            assertEquals("cached-version", e2!!.value)
            assertEquals(2, e2.version)
        }

        @Test
        fun `resolve delegates to findById on cache miss`() {
            val dbDoc = TestDocument(version = 3, id = "doc1", value = "from-db")
            db["doc1"] = dbDoc

            accumulator.startBlock()
            val (existing, nextVersion) = accumulator.resolve("doc1")

            assertEquals(dbDoc, existing)
            assertEquals(4, nextVersion)
        }
    }

    // ===== Archive key deduplication =====

    @Nested
    inner class Deduplication {
        @Test
        fun `archive map key deduplicates by documentId and version`() {
            val original = TestDocument(version = 1, id = "doc1")
            db["doc1"] = original

            // Block 1
            accumulator.startBlock()
            val (e1, nv1) = accumulator.resolve("doc1")
            accumulator.put("doc1", e1, e1!!.copy(version = nv1))

            // Block 2: put the same record again into DB to simulate a new lookup
            // but the accumulator has already cached the v2 from block 1
            accumulator.startBlock()
            val (e2, nv2) = accumulator.resolve("doc1")
            assertEquals(2, e2!!.version)
            accumulator.put("doc1", e2, e2.copy(version = nv2))

            val (_, archived) = accumulator.results()
            // v1 from block 1, v2 from block 2 — different keys
            assertEquals(2, archived.size)
        }
    }

    // ===== Multiple independent records =====

    @Nested
    inner class MultipleRecords {
        @Test
        fun `different recordIds do not interfere with each other`() {
            val docA = TestDocument(version = 1, id = "docA", value = "a-v1")
            val docB = TestDocument(version = 1, id = "docB", value = "b-v1")
            db["docA"] = docA
            db["docB"] = docB

            accumulator.startBlock()

            // Update docA
            val (eA, nvA) = accumulator.resolve("docA")
            accumulator.put("docA", eA, eA!!.copy(version = nvA, value = "a-v2"))

            // Update docB
            val (eB, nvB) = accumulator.resolve("docB")
            accumulator.put("docB", eB, eB!!.copy(version = nvB, value = "b-v2"))

            // Update docA again in same block
            val (eA2, nvA2) = accumulator.resolve("docA")
            assertEquals(2, nvA2) // no increment (already archived this block)
            accumulator.put("docA", eA2, eA2!!.copy(version = nvA2, value = "a-v2-again"))

            val (updated, archived) = accumulator.results()
            assertEquals(2, updated.size)
            assertEquals(2, archived.size) // one archive per record

            val updatedMap = updated.associateBy { it.id }
            assertEquals("a-v2-again", updatedMap["docA"]!!.value)
            assertEquals("b-v2", updatedMap["docB"]!!.value)
        }

        @Test
        fun `archiving one recordId does not affect another in same block`() {
            val docA = TestDocument(version = 1, id = "docA")
            db["docA"] = docA

            accumulator.startBlock()

            // Update docA twice — second should not increment
            val (eA1, nvA1) = accumulator.resolve("docA")
            accumulator.put("docA", eA1, eA1!!.copy(version = nvA1))

            val (eA2, nvA2) = accumulator.resolve("docA")
            assertEquals(2, nvA2) // no increment

            // Now resolve docB (new record) — should still get initialVersion
            val (eB, nvB) = accumulator.resolve("docB")
            assertNull(eB)
            assertEquals(1, nvB)
        }
    }

    // ===== findById returns wrong document =====

    @Nested
    inner class FindByIdMismatchGuard {
        @Test
        fun `resolve throws on mismatched document`() {
            // Simulate findById returning a document whose id doesn't match the requested id
            val wrongDoc = TestDocument(version = 5, id = "wrong-id", value = "wrong")
            val acc = VersionedDocumentAccumulator<TestDocument>(findById = { _ -> wrongDoc })

            acc.startBlock()

            // The guard should fail fast with an IllegalStateException
            assertThrows<IllegalStateException> { acc.resolve("correct-id") }
        }
    }

    // ===== Custom initialVersion =====

    @Nested
    inner class CustomInitialVersion {
        @Test
        fun `initialVersion 0 means new records start at version 0`() {
            val acc =
                VersionedDocumentAccumulator<TestDocument>(findById = { null }, initialVersion = 0)

            acc.startBlock()
            val (existing, nextVersion) = acc.resolve("doc1")

            assertNull(existing)
            assertEquals(0, nextVersion)
        }
    }
}
