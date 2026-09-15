package org.vechain.indexer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.accounts.AccountTotalsSeriesProcessor
import org.vechain.indexer.b3tr.balance.B3trBalanceProcessor
import org.vechain.indexer.b3tr.balance.B3trBalanceService
import org.vechain.indexer.b3tr.gm.GmNftProcessor
import org.vechain.indexer.b3tr.gm.GmNftService
import org.vechain.indexer.b3tr.treasury.TreasuryTransferProcessor
import org.vechain.indexer.b3tr.treasury.TreasuryTransferService
import org.vechain.indexer.b3tr.xAlloc.XAllocResultProcessor
import org.vechain.indexer.b3tr.xAlloc.XAllocResultService
import org.vechain.indexer.blocks.BlockTree
import org.vechain.indexer.blocks.BlockTreeService
import org.vechain.indexer.blocks.BlocksProcessor
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.contracts.ContractProcessor
import org.vechain.indexer.contracts.ContractService
import org.vechain.indexer.explorer.AverageFeesPerUser
import org.vechain.indexer.explorer.BlockUsage
import org.vechain.indexer.explorer.ExplorerProcessor
import org.vechain.indexer.explorer.ExplorerWriteRepository
import org.vechain.indexer.history.HistoryProcessor
import org.vechain.indexer.history.HistoryService
import org.vechain.indexer.nft.NftBlacklistProcessor
import org.vechain.indexer.nft.NftBlacklistService
import org.vechain.indexer.nft.NftProcessor
import org.vechain.indexer.nft.NftService
import org.vechain.indexer.postgres.IndexerStateRepository
import org.vechain.indexer.postgres.PostgresIndexerTables
import org.vechain.indexer.stargate.rewards.TokenRewardProcessor
import org.vechain.indexer.stargate.rewards.TokenRewardService
import org.vechain.indexer.stargate.staking.StargateStakingProcessor
import org.vechain.indexer.stargate.staking.StargateStakingService
import org.vechain.indexer.stargate.token.StargateTokenProcessor
import org.vechain.indexer.stargate.token.StargateTokenService
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedByBlockProcessor
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedByBlockService
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedProcessor
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedService
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedByBlockProcessor
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedByBlockService
import org.vechain.indexer.transfer.TransferService
import org.vechain.indexer.validator.DelegationProcessor
import org.vechain.indexer.validator.DelegationService
import org.vechain.indexer.validator.ValidatorBlockProcessor
import org.vechain.indexer.validator.ValidatorBlockService
import org.vechain.indexer.validator.ValidatorProcessor
import org.vechain.indexer.validator.ValidatorService
import org.vechain.indexer.vevote.VeVoteProcessor
import org.vechain.indexer.vevote.VeVoteWriteRepository

class ProcessorTransactionalAnnotationsTest {

    @Test
    fun `history processor rollback names the postgres transaction manager`() {
        val rollback =
            HistoryProcessor::class.java.getDeclaredMethod("rollback", java.lang.Long.TYPE)
        val transactional = rollback.getAnnotation(Transactional::class.java)
        assertTransactional(transactional)
        assertEquals(PostgresConfig.TRANSACTION_MANAGER, transactional!!.transactionManager)
    }

    @Test
    fun `account totals series processor rollback keeps transactional semantics`() {
        assertRollbackIsTransactional(AccountTotalsSeriesProcessor::class.java)
    }

    @Test
    fun `postgres processor rollback and service save name the postgres transaction manager`() {
        val rollback =
            PostgresProcessor::class.java.getDeclaredMethod("rollback", java.lang.Long.TYPE)
        val saves =
            listOf(
                BlockTreeService::class.java.getDeclaredMethod("save", BlockTree::class.java),
                NftBlacklistService::class.java.getDeclaredMethod("save", List::class.java),
                NftService::class.java.getDeclaredMethod("save", List::class.java),
                ContractService::class.java.getDeclaredMethod("save", List::class.java),
                GmNftService::class.java.getDeclaredMethod("save", List::class.java),
                B3trBalanceService::class.java.getDeclaredMethod("save", List::class.java),
                // The explorer indexer saves its three tables from the repository, not a service.
                ExplorerWriteRepository::class
                    .java
                    .getDeclaredMethod(
                        "save",
                        BlockUsage::class.java,
                        AverageFeesPerUser::class.java,
                        List::class.java,
                    ),
                TreasuryTransferService::class.java.getDeclaredMethod("save", List::class.java),
                // The vevote indexer saves its two tables from the repository, not a service.
                VeVoteWriteRepository::class
                    .java
                    .getDeclaredMethod("save", List::class.java, List::class.java),
                XAllocResultService::class.java.getDeclaredMethod("save", List::class.java),
                HistoryService::class.java.getDeclaredMethod("save", List::class.java),
                ValidatorService::class.java.getDeclaredMethod("save", List::class.java),
                ValidatorBlockService::class.java.getDeclaredMethod("save", List::class.java),
                DelegationService::class.java.getDeclaredMethod("save", List::class.java),
                StargateTokenService::class.java.getDeclaredMethod("save", List::class.java),
                TokenRewardService::class.java.getDeclaredMethod("save", List::class.java),
                VetDelegatedByBlockService::class
                    .java
                    .getDeclaredMethod(
                        "saveRecords",
                        List::class.java,
                    ),
                VthoGeneratedByBlockService::class.java.getDeclaredMethod("save", List::class.java),
                VthoClaimedService::class
                    .java
                    .getDeclaredMethod(
                        "save",
                        VthoClaimedService.Update::class.java,
                    ),
                StargateStakingService::class
                    .java
                    .getDeclaredMethod(
                        "save",
                        StargateStakingService.Update::class.java,
                    ),
            )
        val processors =
            listOf(
                BlocksProcessor::class.java,
                NftBlacklistProcessor::class.java,
                NftProcessor::class.java,
                ContractProcessor::class.java,
                ExplorerProcessor::class.java,
                GmNftProcessor::class.java,
                B3trBalanceProcessor::class.java,
                TreasuryTransferProcessor::class.java,
                VeVoteProcessor::class.java,
                XAllocResultProcessor::class.java,
                HistoryProcessor::class.java,
                ValidatorProcessor::class.java,
                ValidatorBlockProcessor::class.java,
                DelegationProcessor::class.java,
                StargateTokenProcessor::class.java,
                TokenRewardProcessor::class.java,
                VetDelegatedByBlockProcessor::class.java,
                VthoGeneratedByBlockProcessor::class.java,
                VthoClaimedProcessor::class.java,
                StargateStakingProcessor::class.java,
            )
        for (processor in processors) {
            assertEquals(PostgresProcessor::class.java, processor.superclass)
        }
        for (method in listOf(rollback) + saves) {
            val transactional = method.getAnnotation(Transactional::class.java)
            assertTransactional(transactional)
            assertEquals(PostgresConfig.TRANSACTION_MANAGER, transactional!!.transactionManager)
        }
    }

    @Test
    fun `state repository prune and resync pair the table write with the marker in one transaction`() {
        val repo = IndexerStateRepository::class.java
        val prune =
            repo.getDeclaredMethod(
                "prune",
                String::class.java,
                PostgresIndexerTables::class.java,
                java.lang.Long.TYPE,
            )
        val resync =
            repo.getDeclaredMethod(
                "resync",
                String::class.java,
                Integer.TYPE,
                PostgresIndexerTables::class.java,
            )
        for (method in listOf(prune, resync)) {
            val transactional = method.getAnnotation(Transactional::class.java)
            assertTransactional(transactional)
            assertEquals(PostgresConfig.TRANSACTION_MANAGER, transactional!!.transactionManager)
        }
    }

    @Test
    fun `transfer service save keeps transactional semantics`() {
        val saveMethod = TransferService::class.java.getDeclaredMethod("save", List::class.java)
        assertTransactional(saveMethod.getAnnotation(Transactional::class.java))
    }

    private fun assertRollbackIsTransactional(processorClass: Class<*>) {
        val rollbackMethod = processorClass.getDeclaredMethod("rollback", java.lang.Long.TYPE)
        assertTransactional(rollbackMethod.getAnnotation(Transactional::class.java))
    }

    private fun assertTransactional(transactional: Transactional?) {
        assertNotNull(transactional)
        assertEquals(1, transactional!!.rollbackFor.size)
        assertEquals(Exception::class, transactional.rollbackFor.single())
    }
}
