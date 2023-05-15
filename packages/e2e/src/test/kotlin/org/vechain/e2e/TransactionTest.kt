package org.vechain.e2e

import org.junit.jupiter.api.Test
import org.vechain.indexer.model.Transaction
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isGreaterThan
import strikt.assertions.isNotEmpty

class TransactionTest {
    @Test
    fun `get transactions for origin`() {
        val transactions = VeWorldAPIClient.getTransactionsByOrigin("0x435933c8064b4ae76be665428e0307ef2ccfbd68")

        expectThat(transactions).hasSize(8)

        transactions.forEach { transaction ->
            assertValidTransaction(transaction)
        }

        // Get transaction by id
        val transaction = VeWorldAPIClient.getTransactionById(transactions[0].id)

        assertValidTransaction(transaction)

    }

    @Test
    fun `get transactions for origin with pagination`() {
        val transactions =
            VeWorldAPIClient.getTransactionsByOrigin("0x435933c8064b4ae76be665428e0307ef2ccfbd68", size = 1)

        expectThat(transactions).hasSize(1)

        transactions.forEach { transaction ->
            assertValidTransaction(transaction)
        }

    }

    @Test
    fun `get delegated transactions`() {
        val transactions = VeWorldAPIClient.getDelegatedTransactions("0x435933c8064b4ae76be665428e0307ef2ccfbd68")

        expectThat(transactions).hasSize(1)

        transactions.forEach { transaction ->
            assertValidTransaction(transaction)
        }
    }

    @Test
    fun `get sent and delegated transactions`() {
        val transactions = VeWorldAPIClient.getTransactionsByOrigin(
            address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
            includeDelegated = true
        )

        expectThat(transactions).hasSize(9)

        transactions.forEach { transaction ->
            assertValidTransaction(transaction)
        }
    }

    fun assertValidTransaction(transaction: Transaction) {

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