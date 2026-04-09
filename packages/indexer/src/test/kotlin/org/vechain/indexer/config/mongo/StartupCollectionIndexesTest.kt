package org.vechain.indexer.config.mongo

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.springframework.data.mongodb.core.index.IndexOperations
import org.vechain.indexer.accounts.AccountOverview
import org.vechain.indexer.accounts.VetBalance
import org.vechain.indexer.accounts.mongo.AccountOverviewCollectionConfig
import org.vechain.indexer.accounts.mongo.TotalAccountsCollectionConfig
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
        every { mongoTemplate.findOne(any(), AccountOverview::class.java) } returns
            mockk<AccountOverview>(relaxed = true)
        every { mongoTemplate.indexOps(AccountOverview::class.java) } returns indexOperations
        every { indexOperations.ensureIndex(capture(capturedIndexes)) } returns "created"

        AccountOverviewCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
                genesisVetBalanceLoader = genesisVetBalanceLoader,
            )
            .initCollection()

        assertTrue(
            capturedIndexes.any {
                it.indexKeys["blockNumber"] == -1 && it.indexOptions["name"] == "blockNumber_-1"
            }
        )
        verify(exactly = 1) { mongoTemplate.findOne(any(), AccountOverview::class.java) }
        verify(exactly = 0) { genesisVetBalanceLoader.loadGenesisAllocations() }
    }

    @Test
    fun `vet balance creates blockNumber startup index and skips genesis scan when records exist`() {
        val capturedIndexes = mutableListOf<IndexDefinition>()
        every {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(any(), any(), any())
        } returns false
        every { mongoTemplate.collectionExists(VetBalance::class.java) } returns true
        every { mongoTemplate.findOne(any(), VetBalance::class.java) } returns
            mockk<VetBalance>(relaxed = true)
        every { mongoTemplate.indexOps(VetBalance::class.java) } returns indexOperations
        every { indexOperations.ensureIndex(capture(capturedIndexes)) } returns "created"

        VetBalanceCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
                genesisVetBalanceLoader = genesisVetBalanceLoader,
            )
            .initCollection()

        assertTrue(
            capturedIndexes.any {
                it.indexKeys["blockNumber"] == -1 && it.indexOptions["name"] == "blockNumber_-1"
            }
        )
        verify(exactly = 1) { mongoTemplate.findOne(any(), VetBalance::class.java) }
        verify(exactly = 0) { genesisVetBalanceLoader.loadGenesisAllocations() }
    }

    @Test
    fun `total accounts creates blockNumber startup index`() {
        val capturedIndexes = mutableListOf<IndexDefinition>()
        every {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(any(), any(), any())
        } returns false
        every { mongoTemplate.collectionExists(any<Class<*>>()) } returns true
        every { mongoTemplate.indexOps(any<Class<*>>()) } returns indexOperations
        every { indexOperations.ensureIndex(capture(capturedIndexes)) } returns "created"

        TotalAccountsCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
            )
            .initCollection()

        assertTrue(
            capturedIndexes.any {
                it.indexKeys["blockNumber"] == -1 && it.indexOptions["name"] == "blockNumber_-1"
            }
        )
    }

    @Test
    fun `contract creates blockNumber startup index`() {
        val capturedIndexes = mutableListOf<IndexDefinition>()
        every {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(any(), any(), any())
        } returns false
        every { mongoTemplate.collectionExists(Contract::class.java) } returns true
        every { mongoTemplate.indexOps(Contract::class.java) } returns indexOperations
        every { indexOperations.ensureIndex(capture(capturedIndexes)) } returns "created"

        ContractCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
            )
            .initCollection()

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
        every { mongoTemplate.indexOps(Delegation::class.java) } returns indexOperations
        every { indexOperations.ensureIndex(capture(capturedIndexes)) } returns "created"

        DelegationCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
            )
            .initCollection()

        assertTrue(
            capturedIndexes.any {
                it.indexKeys["blockNumber"] == -1 && it.indexOptions["name"] == "blockNumber_-1"
            }
        )
    }
}
