package org.vechain.indexer.config.mongo

import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.springframework.data.mongodb.core.index.IndexField
import org.springframework.data.mongodb.core.index.IndexInfo
import org.springframework.data.mongodb.core.index.IndexOperations
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.accounts.AccountOverview
import org.vechain.indexer.accounts.VetBalance
import org.vechain.indexer.accounts.mongo.AccountOverviewCollectionConfig
import org.vechain.indexer.accounts.mongo.VetBalanceCollectionConfig
import org.vechain.indexer.config.genesis.GenesisVetBalanceLoader
import org.vechain.indexer.contracts.Contract
import org.vechain.indexer.contracts.mongo.ContractCollectionConfig
import org.vechain.indexer.validator.Delegation
import org.vechain.indexer.validator.DelegationCollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@ExtendWith(MockKExtension::class)
class StartupCollectionIndexesTest {

    @MockK lateinit var mongoTemplate: MongoTemplate

    @MockK lateinit var indexOperations: IndexOperations

    @MockK lateinit var indexerVersionService: IndexerVersionService

    @MockK lateinit var genesisVetBalanceLoader: GenesisVetBalanceLoader

    @Test
    fun `account overview creates blockNumber startup index and skips genesis scan when records exist`() {
        val capturedIndexes = mutableListOf<IndexDefinition>()
        every {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(any(), any(), any())
        } returns false
        every { mongoTemplate.collectionExists(AccountOverview::class.java) } returns true
        every { mongoTemplate.getCollectionName(AccountOverview::class.java) } returns
            "account_overviews"
        every {
            mongoTemplate.exists(any<Query>(), AccountOverview::class.java, "account_overviews")
        } returns true
        every { mongoTemplate.indexOps(AccountOverview::class.java) } returns indexOperations
        every { indexOperations.createIndex(capture(capturedIndexes)) } returns "created"

        AccountOverviewCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
                genesisVetBalanceLoader = genesisVetBalanceLoader,
            )
            .apply {
                initCollection()
                createPendingIndexes()
            }

        assertTrue(
            capturedIndexes.any {
                it.indexKeys["blockNumber"] == -1 && it.indexOptions["name"] == "blockNumber_-1"
            }
        )
        verify(exactly = 1) {
            mongoTemplate.exists(any<Query>(), AccountOverview::class.java, "account_overviews")
        }
        verify(exactly = 0) { genesisVetBalanceLoader.loadGenesisAllocations() }
    }

    @Test
    fun `vet balance creates blockNumber startup index and skips genesis scan when records exist`() {
        val capturedIndexes = mutableListOf<IndexDefinition>()
        every {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(any(), any(), any())
        } returns false
        every { mongoTemplate.collectionExists(VetBalance::class.java) } returns true
        every { mongoTemplate.getCollectionName(VetBalance::class.java) } returns "vet_balances"
        every { mongoTemplate.exists(any<Query>(), VetBalance::class.java, "vet_balances") } returns
            true
        every { mongoTemplate.indexOps(VetBalance::class.java) } returns indexOperations
        every { indexOperations.createIndex(capture(capturedIndexes)) } returns "created"

        VetBalanceCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
                genesisVetBalanceLoader = genesisVetBalanceLoader,
            )
            .apply {
                initCollection()
                createPendingIndexes()
            }

        assertTrue(
            capturedIndexes.any {
                it.indexKeys["blockNumber"] == -1 && it.indexOptions["name"] == "blockNumber_-1"
            }
        )
        verify(exactly = 1) {
            mongoTemplate.exists(any<Query>(), VetBalance::class.java, "vet_balances")
        }
        verify(exactly = 0) { genesisVetBalanceLoader.loadGenesisAllocations() }
    }

    @Test
    fun `contract creates blockNumber startup index`() {
        val capturedIndexes = mutableListOf<IndexDefinition>()
        every {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(any(), any(), any())
        } returns false
        every { mongoTemplate.collectionExists(Contract::class.java) } returns true
        every { mongoTemplate.getCollectionName(Contract::class.java) } returns "contracts"
        every { mongoTemplate.indexOps(Contract::class.java) } returns indexOperations
        every { indexOperations.createIndex(capture(capturedIndexes)) } returns "created"

        ContractCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
            )
            .apply {
                initCollection()
                createPendingIndexes()
            }

        assertTrue(
            capturedIndexes.any {
                it.indexKeys["blockNumber"] == -1 && it.indexOptions["name"] == "blockNumber_-1"
            }
        )
    }

    @Test
    fun `delegation creates blockNumber startup index`() {
        val capturedIndexes = mutableListOf<IndexDefinition>()
        every {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(any(), any(), any())
        } returns false
        every { mongoTemplate.collectionExists(Delegation::class.java) } returns true
        every { mongoTemplate.getCollectionName(Delegation::class.java) } returns "delegations"
        every { mongoTemplate.indexOps(Delegation::class.java) } returns indexOperations
        every { indexOperations.createIndex(capture(capturedIndexes)) } returns "created"

        DelegationCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
            )
            .apply {
                initCollection()
                createPendingIndexes()
            }

        assertTrue(
            capturedIndexes.any {
                it.indexKeys["blockNumber"] == -1 && it.indexOptions["name"] == "blockNumber_-1"
            }
        )
    }

    @Test
    fun `removeStaleIndexes drops legacy renamed index before createPendingIndexes runs`() {
        // Simulates the upgrade scenario from PR #1323: the collection still has a legacy index
        // (different name, same key spec). MongoDB would otherwise reject createIndex with
        // IndexOptionsConflict (error 85). Stale removal must happen before create.
        val createdIndexes = mutableListOf<IndexDefinition>()
        val legacyIndex =
            IndexInfo(
                listOf(IndexField.create("blockNumber", Sort.Direction.DESC)),
                "blockNumber_-1_legacy",
                false,
                false,
                "",
            )
        every {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(any(), any(), any())
        } returns false
        every { mongoTemplate.collectionExists(Contract::class.java) } returns true
        every { mongoTemplate.getCollectionName(Contract::class.java) } returns "contracts"
        every { mongoTemplate.indexOps(Contract::class.java) } returns indexOperations
        every { mongoTemplate.indexOps("contracts") } returns indexOperations
        every { indexOperations.indexInfo } returns listOf(legacyIndex)
        every { indexOperations.dropIndex("blockNumber_-1_legacy") } just Runs
        every { indexOperations.createIndex(capture(createdIndexes)) } returns "created"

        ContractCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
            )
            .apply {
                initCollection()
                removeStaleIndexes()
                createPendingIndexes()
            }

        verifyOrder {
            indexOperations.dropIndex("blockNumber_-1_legacy")
            indexOperations.createIndex(any())
        }
        assertTrue(
            createdIndexes.any { it.indexOptions["name"] == "blockNumber_-1" },
            "expected new blockNumber_-1 index to be created after legacy is dropped",
        )
    }

    @Test
    fun `removeStaleIndexes drops same-name index when partial filter has drifted`() {
        // Same name and same keys, but the existing index has a stale partial filter
        // (legacy INDEXED_DOCUMENT_PARTIAL_FILTER) while the new spec uses a different
        // partial filter. MongoDB would otherwise reject createIndex with
        // IndexKeySpecsConflict (error 86). Drop-and-recreate is the only path forward.
        val createdIndexes = mutableListOf<IndexDefinition>()
        val driftedIndex =
            IndexInfo.indexInfoOf(
                Document("name", "blockNumber_-1")
                    .append("key", Document("blockNumber", -1))
                    .append(
                        "partialFilterExpression",
                        Document("blockNumber", Document("\$exists", true)),
                    )
            )
        every {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(any(), any(), any())
        } returns false
        every { mongoTemplate.collectionExists(Contract::class.java) } returns true
        every { mongoTemplate.getCollectionName(Contract::class.java) } returns "contracts"
        every { mongoTemplate.indexOps(Contract::class.java) } returns indexOperations
        every { mongoTemplate.indexOps("contracts") } returns indexOperations
        every { indexOperations.indexInfo } returns listOf(driftedIndex)
        every { indexOperations.dropIndex("blockNumber_-1") } just Runs
        every { indexOperations.createIndex(capture(createdIndexes)) } returns "created"

        // The Contract config registers blockNumber_-1 with no partial filter override
        // (default INDEXED_DOCUMENT_PARTIAL_FILTER). Build a TestConfig that uses a *different*
        // partial filter so the drift is observable.
        TestPartialFilterDriftConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
            )
            .apply {
                initCollection()
                removeStaleIndexes()
                createPendingIndexes()
            }

        verifyOrder {
            indexOperations.dropIndex("blockNumber_-1")
            indexOperations.createIndex(any())
        }
        assertTrue(
            createdIndexes.any { it.indexOptions["name"] == "blockNumber_-1" },
            "expected blockNumber_-1 index to be recreated with the new partial filter",
        )
    }

    /**
     * Test fixture: a CollectionConfig that registers a single `blockNumber_-1` index whose partial
     * filter does not match the legacy `INDEXED_DOCUMENT_PARTIAL_FILTER`, exercising the
     * option-drift detection path of [removeStaleIndexes].
     */
    private class TestPartialFilterDriftConfig(
        mongoTemplate: MongoTemplate,
        appCoroutineScope: CoroutineScope,
        private val indexerVersionService: org.vechain.indexer.version.IndexerVersionService,
    ) :
        org.vechain.indexer.config.mongo.CollectionConfig(
            mongoTemplate,
            appCoroutineScope,
            Contract::class.java,
        ) {
        override fun initCollection() {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                indexerName = "test",
                Contract::class.java,
                1,
            )
            ensureCollection()
            ensureIndexes(
                listOf(buildIndex("blockNumber" to Sort.Direction.DESC)),
                partialFilter = Document("status", Document("\$exists", true)),
            )
        }
    }
}
