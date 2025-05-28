package org.vechain.e2e

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.model.IndexedTransaction
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*

class TransactionTest {

    @BeforeEach
    fun `perform healthcheck`() {
        VeWorldAPIClient.performIndexerHealthCheck("TransactionIndexer")
    }

    @Test
    fun `get transactions for origin`() {
        val transactions =
            VeWorldAPIClient.getTransactionsByOrigin("0x435933c8064b4ae76be665428e0307ef2ccfbd68")
        val txs: List<IndexedTransaction> = transactions.data

        expectThat(txs).hasSize(8)
        expectThat(transactions.pagination.hasNext).isFalse()

        txs.forEach { transaction: IndexedTransaction -> assertValidTransaction(transaction) }

        // Get transaction by id
        val transaction = VeWorldAPIClient.getTransactionById(txs[0].id)

        assertValidTransaction(transaction)
    }

    @Test
    fun `get transactions for origin with pagination`() {
        val transactions =
            VeWorldAPIClient.getTransactionsByOrigin(
                "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
                size = 1,
            )
        val txs: List<IndexedTransaction> = transactions.data

        expectThat(txs).hasSize(1)
        expectThat(transactions.pagination.hasNext).isTrue()

        txs.forEach { transaction: IndexedTransaction -> assertValidTransaction(transaction) }
    }

    @Test
    fun `get delegated transactions`() {
        val transactions =
            VeWorldAPIClient.getDelegatedTransactions("0x435933c8064b4ae76be665428e0307ef2ccfbd68")
        val txs: List<IndexedTransaction> = transactions.data

        expectThat(txs).hasSize(1)
        expectThat(transactions.pagination.hasNext).isFalse()

        txs.forEach { transaction: IndexedTransaction -> assertValidTransaction(transaction) }
    }

    @Test
    fun `get sent and delegated transactions`() {
        val transactions =
            VeWorldAPIClient.getTransactionsByOrigin(
                address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
                includeDelegated = true,
            )
        val txs: List<IndexedTransaction> = transactions.data

        expectThat(txs).hasSize(9)
        expectThat(transactions.pagination.hasNext).isFalse()

        txs.forEach { transaction: IndexedTransaction -> assertValidTransaction(transaction) }
    }

    fun assertValidTransaction(transaction: IndexedTransaction) {
        expect {
            that(transaction.id).isNotEmpty()
            that(transaction.origin).isNotEmpty()
            that(transaction.nonce).isNotEmpty()
            that(transaction.gasUsed).isGreaterThan(0)
            that(transaction.clauses).isNotEmpty()
            that(transaction.outputs).isNotEmpty()
        }
    }
}
