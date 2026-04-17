package org.vechain.indexer.transaction

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.SliceImpl
import org.vechain.indexer.thor.Address
import strikt.api.expectThat
import strikt.assertions.isSameInstanceAs

class TransactionServiceTest {
    private val transactionRepository: TransactionRepository = mockk()
    private val service = TransactionService(transactionRepository)

    @Test
    fun `findByOriginOrDelegator delegates to origin query when includeDelegated is false`() {
        val pageable = Pageable.ofSize(10)
        val expected = SliceImpl<IndexedTransaction>(emptyList(), pageable, false)
        val address = Address("0x0000000000000000000000000000000000000001")
        every { transactionRepository.findByOrigin(address.value, pageable) } returns expected

        expectThat(service.findByOriginOrDelegator(address, false, pageable))
            .isSameInstanceAs(expected)
    }
}
