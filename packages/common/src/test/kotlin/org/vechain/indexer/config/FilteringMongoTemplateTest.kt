package org.vechain.indexer.config

import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.TypedAggregation
import org.springframework.data.mongodb.core.convert.MongoConverter
import org.springframework.data.mongodb.core.mapping.Document as MongoDocument
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.VersionedDocument
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isTrue

// --- Test fixture entities ---

@MongoDocument(collection = "test_indexed_docs")
data class TestIndexedDoc(
    @Id val id: String,
    override val blockId: String = "0xblock",
    override val blockNumber: Long = 1L,
    override val blockTimestamp: Long = 1000L,
) : IndexedDocument

@MongoDocument(collection = "test_plain_docs")
data class TestPlainDoc(@Id val id: String, val value: String = "plain")

@MongoDocument(collection = "test_versioned_docs")
data class TestVersionedDoc(
    @Id val id: String,
    override val blockId: String = "0xblock",
    override val blockNumber: Long = 1L,
    override val blockTimestamp: Long = 1000L,
    override val version: Int = 1,
) : VersionedDocument {
    override fun getDocumentId(): String = id
}

@MongoDocument(collection = "test_custom_id_docs")
data class TestCustomIdDoc(
    @Id val proposalId: String,
    override val blockId: String = "0xblock",
    override val blockNumber: Long = 1L,
    override val blockTimestamp: Long = 1000L,
) : IndexedDocument

// --- Test configuration ---

@Configuration
open class FilteringMongoTemplateTestConfig {
    @Bean
    open fun filteringMongoTemplate(
        dbFactory: MongoDatabaseFactory,
        converter: MongoConverter,
    ): FilteringMongoTemplate = FilteringMongoTemplate(dbFactory, converter)
}

@SpringBootApplication open class TestApplication

@DataMongoTest
@ActiveProfiles("test")
@ContextConfiguration(classes = [TestApplication::class, FilteringMongoTemplateTestConfig::class])
internal class FilteringMongoTemplateTest {

    @Autowired private lateinit var template: FilteringMongoTemplate

    private val doc1 = TestIndexedDoc(id = "doc1", blockNumber = 100)
    private val doc2 = TestIndexedDoc(id = "doc2", blockNumber = 200)

    @BeforeEach
    fun setUp() {
        template.dropCollection("test_indexed_docs")
        template.dropCollection("test_plain_docs")
        template.dropCollection("test_custom_id_docs")
        template.dropCollection("test_versioned_docs")
    }

    private fun insertCheckpoint(collectionName: String) {
        val checkpoint = Document("_id", "__checkpoint__").append("checkpointBlockNumber", 200)
        template.getCollection(collectionName).insertOne(checkpoint)
    }

    private fun seedIndexedDocs() {
        template.insert(doc1)
        template.insert(doc2)
        insertCheckpoint("test_indexed_docs")
    }

    // --- 1. Exclusion filter is applied (IndexedDocument queries) ---

    @Nested
    inner class ExclusionFilter {

        @Test
        fun `find on IndexedDocument excludes checkpoint`() {
            seedIndexedDocs()

            val result = template.find(Query(), TestIndexedDoc::class.java)

            expectThat(result.map { it.id }).containsExactlyInAnyOrder("doc1", "doc2")
        }

        @Test
        fun `findOne on IndexedDocument excludes checkpoint`() {
            template.insert(doc1)
            insertCheckpoint("test_indexed_docs")

            val result = template.findOne(Query(), TestIndexedDoc::class.java)

            expectThat(result).isNotNull().and { get { id }.isEqualTo("doc1") }
        }

        @Test
        fun `findAll on IndexedDocument excludes checkpoint`() {
            seedIndexedDocs()

            val result = template.findAll(TestIndexedDoc::class.java)

            expectThat(result.map { it.id }).containsExactlyInAnyOrder("doc1", "doc2")
        }

        @Test
        fun `count on IndexedDocument excludes checkpoint`() {
            seedIndexedDocs()

            val result = template.count(Query(), TestIndexedDoc::class.java)

            expectThat(result).isEqualTo(2L)
        }

        @Test
        fun `exists on IndexedDocument excludes checkpoint`() {
            template.insert(doc1)
            insertCheckpoint("test_indexed_docs")

            val result = template.exists(Query(), TestIndexedDoc::class.java)

            expectThat(result).isTrue()
        }

        @Test
        fun `stream on IndexedDocument excludes checkpoint`() {
            seedIndexedDocs()

            val result =
                template.stream(Query(), TestIndexedDoc::class.java).use { stream ->
                    stream.map { it.id }.toList()
                }

            expectThat(result).containsExactlyInAnyOrder("doc1", "doc2")
        }

        @Test
        fun `findDistinct on IndexedDocument excludes checkpoint`() {
            seedIndexedDocs()

            val result =
                template.findDistinct(
                    Query(),
                    "blockNumber",
                    "test_indexed_docs",
                    TestIndexedDoc::class.java,
                    Long::class.java,
                )

            expectThat(result).containsExactlyInAnyOrder(100L, 200L)
        }
    }

    // --- 2. Exclusion filter is NOT applied (non-IndexedDocument queries) ---

    @Nested
    inner class NonIndexedDocumentPassthrough {

        @Test
        fun `find on non-IndexedDocument does not add filter`() {
            val plain1 = TestPlainDoc(id = "p1", value = "a")
            val plain2 = TestPlainDoc(id = "p2", value = "b")
            template.insert(plain1)
            template.insert(plain2)

            val result = template.find(Query(), TestPlainDoc::class.java)

            expectThat(result.map { it.id }).containsExactlyInAnyOrder("p1", "p2")
        }

        @Test
        fun `count on non-IndexedDocument does not add filter`() {
            template.insert(TestPlainDoc(id = "p1"))
            template.insert(TestPlainDoc(id = "p2"))

            val result = template.count(Query(), TestPlainDoc::class.java)

            expectThat(result).isEqualTo(2L)
        }

        @Test
        fun `findAll on non-IndexedDocument does not add filter`() {
            template.insert(TestPlainDoc(id = "p1"))
            template.insert(TestPlainDoc(id = "p2"))

            val result = template.findAll(TestPlainDoc::class.java)

            expectThat(result.map { it.id }).containsExactlyInAnyOrder("p1", "p2")
        }
    }

    // --- 3. ID-based query bypass ---

    @Nested
    inner class IdCriteriaBypass {

        @Test
        fun `find with _id criteria skips exclusion filter`() {
            seedIndexedDocs()

            val result =
                template.find(Query(Criteria.where("_id").`is`("doc1")), TestIndexedDoc::class.java)

            expectThat(result.map { it.id }).isEqualTo(listOf("doc1"))
        }

        @Test
        fun `find with id criteria skips exclusion filter`() {
            seedIndexedDocs()

            val result =
                template.find(Query(Criteria.where("id").`is`("doc1")), TestIndexedDoc::class.java)

            expectThat(result.map { it.id }).isEqualTo(listOf("doc1"))
        }

        @Test
        fun `find with custom @Id property skips exclusion filter`() {
            val customDoc = TestCustomIdDoc(proposalId = "p1", blockNumber = 100)
            template.insert(customDoc)
            insertCheckpoint("test_custom_id_docs")

            val result =
                template.find(
                    Query(Criteria.where("proposalId").`is`("p1")),
                    TestCustomIdDoc::class.java,
                )

            expectThat(result.map { it.proposalId }).isEqualTo(listOf("p1"))
        }
    }

    // --- 4. blockNumber-already-present bypass ---

    @Nested
    inner class BlockNumberCriteriaBypass {

        @Test
        fun `find with blockNumber gte criteria does not add duplicate`() {
            seedIndexedDocs()

            val result =
                template.find(
                    Query(Criteria.where("blockNumber").gte(100)),
                    TestIndexedDoc::class.java,
                )

            expectThat(result.map { it.id }).containsExactlyInAnyOrder("doc1", "doc2")
        }

        @Test
        fun `find with blockNumber lte criteria does not add duplicate`() {
            seedIndexedDocs()

            val result =
                template.find(
                    Query(Criteria.where("blockNumber").lte(200)),
                    TestIndexedDoc::class.java,
                )

            expectThat(result.map { it.id }).containsExactlyInAnyOrder("doc1", "doc2")
        }

        @Test
        fun `count with blockNumber criteria does not add duplicate`() {
            seedIndexedDocs()

            val result =
                template.count(
                    Query(Criteria.where("blockNumber").gte(100)),
                    TestIndexedDoc::class.java,
                )

            expectThat(result).isEqualTo(2L)
        }
    }

    // --- 5. Aggregation methods ---

    @Nested
    inner class AggregationFiltering {

        @Test
        fun `aggregate by inputType prepends blockNumber exists match`() {
            seedIndexedDocs()

            val agg =
                Aggregation.newAggregation(
                    TestIndexedDoc::class.java,
                    Aggregation.project("id", "blockNumber"),
                )
            val result = template.aggregate(agg, TestIndexedDoc::class.java)

            expectThat(result.mappedResults.map { it.id }).containsExactlyInAnyOrder("doc1", "doc2")
        }

        @Test
        fun `aggregate by collectionName prepends match for IndexedDocument collection`() {
            seedIndexedDocs()

            val agg = Aggregation.newAggregation(Aggregation.project("blockNumber"))
            val result = template.aggregate(agg, "test_indexed_docs", TestIndexedDoc::class.java)

            expectThat(result.mappedResults).and { get { size }.isEqualTo(2) }
        }

        @Test
        fun `aggregate by collectionName does NOT prepend match for non-IndexedDocument collection`() {
            template.insert(TestPlainDoc(id = "p1", value = "a"))
            template.insert(TestPlainDoc(id = "p2", value = "b"))

            val agg = Aggregation.newAggregation(Aggregation.project("value"))
            val result = template.aggregate(agg, "test_plain_docs", TestPlainDoc::class.java)

            expectThat(result.mappedResults.size).isEqualTo(2)
        }

        @Test
        fun `TypedAggregation on IndexedDocument prepends match`() {
            seedIndexedDocs()

            val agg =
                TypedAggregation.newAggregation(
                    TestIndexedDoc::class.java,
                    Aggregation.project("id", "blockNumber"),
                )
            val result = template.aggregate(agg, TestIndexedDoc::class.java)

            expectThat(result.mappedResults.map { it.id }).containsExactlyInAnyOrder("doc1", "doc2")
        }

        @Test
        fun `aggregateStream on IndexedDocument excludes checkpoint`() {
            seedIndexedDocs()

            val agg =
                Aggregation.newAggregation(
                    TestIndexedDoc::class.java,
                    Aggregation.project("id", "blockNumber"),
                )
            val result =
                template
                    .aggregateStream(agg, TestIndexedDoc::class.java, TestIndexedDoc::class.java)
                    .use { stream -> stream.map { it.id }.toList() }

            expectThat(result).containsExactlyInAnyOrder("doc1", "doc2")
        }

        @Test
        fun `aggregateStream by collectionName excludes checkpoint for IndexedDocument collection`() {
            seedIndexedDocs()

            val agg = Aggregation.newAggregation(Aggregation.project("blockNumber"))
            val result =
                template
                    .aggregateStream(agg, "test_indexed_docs", TestIndexedDoc::class.java)
                    .use { stream -> stream.toList() }

            expectThat(result.size).isEqualTo(2)
        }
    }

    // --- 6. Fluent API (query()) ---

    @Nested
    inner class FluentQueryApi {

        @Test
        fun `query() matching(empty) all() excludes checkpoint`() {
            seedIndexedDocs()

            val result = template.query(TestIndexedDoc::class.java).matching(Query()).all()

            expectThat(result.map { it.id }).containsExactlyInAnyOrder("doc1", "doc2")
        }

        @Test
        fun `query() all() excludes checkpoint (no matching call)`() {
            seedIndexedDocs()

            val result = template.query(TestIndexedDoc::class.java).all()

            expectThat(result.map { it.id }).containsExactlyInAnyOrder("doc1", "doc2")
        }

        @Test
        fun `query() matching(id query) all() skips filter`() {
            seedIndexedDocs()

            val result =
                template
                    .query(TestIndexedDoc::class.java)
                    .matching(Query(Criteria.where("_id").`is`("doc1")))
                    .all()

            expectThat(result.map { it.id }).isEqualTo(listOf("doc1"))
        }

        @Test
        fun `query() matching(blockNumber query) all() does not duplicate`() {
            seedIndexedDocs()

            val result =
                template
                    .query(TestIndexedDoc::class.java)
                    .matching(Query(Criteria.where("blockNumber").gte(100)))
                    .all()

            expectThat(result.map { it.id }).containsExactlyInAnyOrder("doc1", "doc2")
        }

        @Test
        fun `query() count() excludes checkpoint`() {
            seedIndexedDocs()

            val result = template.query(TestIndexedDoc::class.java).count()

            expectThat(result).isEqualTo(2L)
        }

        @Test
        fun `query() exists() excludes checkpoint`() {
            seedIndexedDocs()

            val result = template.query(TestIndexedDoc::class.java).exists()

            expectThat(result).isTrue()
        }

        @Test
        fun `query(nonIndexedDoc) all() returns all`() {
            template.insert(TestPlainDoc(id = "p1"))
            template.insert(TestPlainDoc(id = "p2"))

            val result = template.query(TestPlainDoc::class.java).all()

            expectThat(result.map { it.id }).containsExactlyInAnyOrder("p1", "p2")
        }

        @Test
        fun `query() distinct(blockNumber) all() excludes checkpoint`() {
            seedIndexedDocs()

            val result =
                template
                    .query(TestIndexedDoc::class.java)
                    .distinct("blockNumber")
                    .`as`(Long::class.java)
                    .all()

            expectThat(result).containsExactlyInAnyOrder(100L, 200L)
        }
    }

    // --- 7. Caching behaviour ---

    @Nested
    inner class Caching {

        @Test
        fun `shouldFilter result is cached per class`() {
            seedIndexedDocs()

            // First call
            val result1 = template.find(Query(), TestIndexedDoc::class.java)
            expectThat(result1.map { it.id }).containsExactlyInAnyOrder("doc1", "doc2")

            // Second call — cache hit, should still filter
            val result2 = template.find(Query(), TestIndexedDoc::class.java)
            expectThat(result2.map { it.id }).containsExactlyInAnyOrder("doc1", "doc2")
        }

        @Test
        fun `shouldFilterCollection result is cached per collection name`() {
            seedIndexedDocs()

            val agg = Aggregation.newAggregation(Aggregation.project("blockNumber"))

            // First call
            val result1 = template.aggregate(agg, "test_indexed_docs", TestIndexedDoc::class.java)
            expectThat(result1.mappedResults.size).isEqualTo(2)

            // Second call — cache hit
            val agg2 = Aggregation.newAggregation(Aggregation.project("blockNumber"))
            val result2 = template.aggregate(agg2, "test_indexed_docs", TestIndexedDoc::class.java)
            expectThat(result2.mappedResults.size).isEqualTo(2)
        }
    }

    // --- 8. Archive filtering (VersionedDocument) ---

    @Nested
    inner class ArchiveFiltering {

        private val vDoc1 = TestVersionedDoc(id = "v1", blockNumber = 100, version = 1)
        private val vDoc2 = TestVersionedDoc(id = "v2", blockNumber = 200, version = 2)

        private fun seedVersionedDocs() {
            template.insert(vDoc1)
            template.insert(vDoc2)
            // Insert an archive document directly via raw driver
            val archiveDoc =
                Document("_id", "archive-v1-1")
                    .append("blockId", "0xblock")
                    .append("blockNumber", 100L)
                    .append("blockTimestamp", 1000L)
                    .append("version", 1)
                    .append("_isArchive", true)
                    .append("_originalDocId", "v1")
            template.getCollection("test_versioned_docs").insertOne(archiveDoc)
        }

        @Test
        fun `find on VersionedDocument excludes archive documents`() {
            seedVersionedDocs()

            val result = template.find(Query(), TestVersionedDoc::class.java)

            expectThat(result.map { it.id }).containsExactlyInAnyOrder("v1", "v2")
        }

        @Test
        fun `findAll on VersionedDocument excludes archive documents`() {
            seedVersionedDocs()

            val result = template.findAll(TestVersionedDoc::class.java)

            expectThat(result.map { it.id }).containsExactlyInAnyOrder("v1", "v2")
        }

        @Test
        fun `count on VersionedDocument excludes archive documents`() {
            seedVersionedDocs()

            val result = template.count(Query(), TestVersionedDoc::class.java)

            expectThat(result).isEqualTo(2L)
        }

        @Test
        fun `find with _id criteria on VersionedDocument skips archive filter`() {
            seedVersionedDocs()

            val result =
                template.find(Query(Criteria.where("_id").`is`("v1")), TestVersionedDoc::class.java)

            expectThat(result.map { it.id }).isEqualTo(listOf("v1"))
        }

        @Test
        fun `archive filter is not applied to non-VersionedDocument entities`() {
            // Insert a normal IndexedDocument with _isArchive field (shouldn't be filtered)
            val doc = TestIndexedDoc(id = "idx1", blockNumber = 100)
            template.insert(doc)
            val archiveLikeDoc =
                Document("_id", "idx-archive")
                    .append("blockId", "0xblock")
                    .append("blockNumber", 50L)
                    .append("blockTimestamp", 1000L)
                    .append("_isArchive", true)
            template.getCollection("test_indexed_docs").insertOne(archiveLikeDoc)

            // Non-VersionedDocument IndexedDocument should NOT filter _isArchive
            val result = template.find(Query(), TestIndexedDoc::class.java)

            expectThat(result.map { it.id }).containsExactlyInAnyOrder("idx1", "idx-archive")
        }

        @Test
        fun `aggregate on VersionedDocument excludes archive documents`() {
            seedVersionedDocs()

            val agg =
                Aggregation.newAggregation(
                    TestVersionedDoc::class.java,
                    Aggregation.project("id", "blockNumber"),
                )
            val result = template.aggregate(agg, TestVersionedDoc::class.java)

            expectThat(result.mappedResults.map { it.id }).containsExactlyInAnyOrder("v1", "v2")
        }
    }

    // --- 9. Edge cases ---

    @Nested
    inner class EdgeCases {

        @Test
        fun `empty collection returns empty list, not error`() {
            template.createCollection("test_indexed_docs")

            val result = template.find(Query(), TestIndexedDoc::class.java)

            expectThat(result).isEmpty()
        }

        @Test
        fun `collection with only checkpoint returns empty`() {
            insertCheckpoint("test_indexed_docs")

            val result = template.find(Query(), TestIndexedDoc::class.java)
            val count = template.count(Query(), TestIndexedDoc::class.java)

            expectThat(result).isEmpty()
            expectThat(count).isEqualTo(0L)
        }

        @Test
        fun `findAll on empty IndexedDocument collection returns empty`() {
            template.createCollection("test_indexed_docs")

            val result = template.findAll(TestIndexedDoc::class.java)

            expectThat(result).isEmpty()
        }
    }
}
