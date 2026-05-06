package org.vechain.indexer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.accounts.AccountTotalsSeriesProcessor
import org.vechain.indexer.history.HistoryProcessor
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
