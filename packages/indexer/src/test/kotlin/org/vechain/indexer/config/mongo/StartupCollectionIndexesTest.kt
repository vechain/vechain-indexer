package org.vechain.indexer.config.mongo

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.IndexDefinition
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
            .initCollection()

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
            .initCollection()

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
        every { mongoTemplate.getCollectionName(Delegation::class.java) } returns "delegations"
        every { mongoTemplate.indexOps(Delegation::class.java) } returns indexOperations
        every { indexOperations.createIndex(capture(capturedIndexes)) } returns "created"

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
