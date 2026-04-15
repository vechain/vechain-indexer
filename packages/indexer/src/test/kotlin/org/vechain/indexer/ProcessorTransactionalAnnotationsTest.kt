package org.vechain.indexer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.accounts.AccountTotalsSeriesProcessor
import org.vechain.indexer.history.HistoryProcessor

class ProcessorTransactionalAnnotationsTest {

    @Test
    fun `history processor rollback keeps transactional semantics`() {
        assertRollbackIsTransactional(HistoryProcessor::class.java)
    }

    @Test
    fun `account totals series processor rollback keeps transactional semantics`() {
        assertRollbackIsTransactional(AccountTotalsSeriesProcessor::class.java)
    }

    private fun assertRollbackIsTransactional(processorClass: Class<*>) {
        val rollbackMethod = processorClass.getDeclaredMethod("rollback", java.lang.Long.TYPE)
        val transactional = rollbackMethod.getAnnotation(Transactional::class.java)

        assertNotNull(transactional)
        assertEquals(1, transactional.rollbackFor.size)
        assertEquals(Exception::class, transactional.rollbackFor.single())
    }
}
