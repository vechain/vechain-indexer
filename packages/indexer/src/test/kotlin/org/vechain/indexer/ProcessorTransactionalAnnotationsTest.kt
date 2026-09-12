package org.vechain.indexer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.accounts.AccountTotalsSeriesProcessor
import org.vechain.indexer.blocks.BlockTree
import org.vechain.indexer.blocks.BlockTreeService
import org.vechain.indexer.blocks.BlocksProcessor
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.history.HistoryProcessor
import org.vechain.indexer.nft.NftBlacklistProcessor
import org.vechain.indexer.nft.NftBlacklistService
import org.vechain.indexer.nft.NftProcessor
import org.vechain.indexer.nft.NftService
import org.vechain.indexer.transfer.TransferService

class ProcessorTransactionalAnnotationsTest {

    @Test
    fun `history processor rollback keeps transactional semantics`() {
        assertRollbackIsTransactional(HistoryProcessor::class.java)
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
            )
        val processors =
            listOf(
                BlocksProcessor::class.java,
                NftBlacklistProcessor::class.java,
                NftProcessor::class.java,
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
