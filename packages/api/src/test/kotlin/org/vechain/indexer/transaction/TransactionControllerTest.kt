package org.vechain.indexer.transaction

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.Page
import org.springframework.http.HttpHeaders
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.rest.MAX_CACHE_AGE_SECONDS
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.PaginationDetail
import org.vechain.indexer.rest.VOLATILE_CACHE_CONTROL
import org.vechain.indexer.thor.Address

@ExtendWith(MockKExtension::class)
internal class TransactionControllerTest {

    @MockK lateinit var transactionService: TransactionService

    private lateinit var controller: TransactionController

    private val txId = "0x" + "a".repeat(64)

    @BeforeEach
    fun setUp() {
        controller = TransactionController(transactionService)
    }

    private fun transaction(blockTimestamp: Long) =
        IndexedTransaction(
            id = txId,
            blockId = "0xblock",
            blockNumber = 1000L,
            blockTimestamp = blockTimestamp,
            transactionIndex = 0L,
            type = null,
            size = 1L,
            chainTag = 1L,
            blockRef = "0xblockref",
            expiration = 1L,
            clauses = emptyList(),
            gasPriceCoef = null,
            gas = 1L,
            maxFeePerGas = null,
            maxPriorityFeePerGas = null,
            dependsOn = null,
            nonce = "0x1",
            gasUsed = 1L,
            gasPayer = "0x0000000000000000000000000000000000000001",
            paid = "0x0",
            reward = "0x0",
            reverted = false,
            origin = "0x0000000000000000000000000000000000000001",
            outputs = emptyList(),
        )

    private fun cacheControlFor(blockTimestamp: Long): String? {
        every { transactionService.findById(txId) } returns transaction(blockTimestamp)
        return controller.getTransactionById(txId).headers.getFirst(HttpHeaders.CACHE_CONTROL)
    }

    @Test
    fun `an old transaction is cacheable up to the cap`() {
        // Genesis-era stamp, so the cap applies whenever this test runs.
        assertEquals("public, max-age=$MAX_CACHE_AGE_SECONDS", cacheControlFor(1_530_000_000L))
    }

    @Test
    fun `a transaction included moments ago is barely cacheable`() {
        val header = cacheControlFor(Instant.now().epochSecond - 20)

        // Wall-clock bound rather than exact: the age is computed against the real clock.
        val maxAge = header?.substringAfter("max-age=")?.toLong()
        assertTrue(maxAge != null && maxAge in 20L..120L, "unexpected header: $header")
    }

    @Test
    fun `the transaction is returned as the response body`() {
        val expected = transaction(1_700_000_000L)
        every { transactionService.findById(txId) } returns expected

        val result = controller.getTransactionById(txId)

        assertEquals(200, result.statusCode.value())
        assertEquals(expected, result.body)
    }

    @Test
    fun `an unknown transaction is a not found rather than a cacheable response`() {
        every { transactionService.findById(txId) } returns null

        assertThrows<ResourceNotFoundException> { controller.getTransactionById(txId) }
    }

    @Test
    fun `the latest transactions are cacheable for a block, not indefinitely`() {
        every { transactionService.findLatest(any(), any()) } returns
            PaginatedResponse(emptyList(), PaginationDetail(hasNext = false))

        val result = controller.getLatestTransactions(size = null, cursor = null)

        assertEquals(VOLATILE_CACHE_CONTROL, result.headers.getFirst(HttpHeaders.CACHE_CONTROL))
    }

    @Test
    fun `contract transactions are cacheable for a block, not indefinitely`() {
        every { transactionService.findByContractAddress(any(), any()) } returns
            Page.empty<IndexedTransaction>()

        val result =
            controller.getTransactionsByContract(
                contractAddress = Address("0x0000000000000000000000000000000000000001"),
                page = null,
                size = null,
                direction = null,
            )

        assertEquals(VOLATILE_CACHE_CONTROL, result.headers.getFirst(HttpHeaders.CACHE_CONTROL))
    }
}
