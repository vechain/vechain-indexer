package org.vechain.indexer.transaction

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address

internal class TransactionV2ControllerTest {

    private val service: TransactionService = mockk()
    private val controller = TransactionV2Controller(service)
    private val address = Address("0x0000000000000000000000000000000000000001")

    @Test
    fun `contract listing tolerates a stale minute, as v1 does`() {
        val declared =
            TransactionV2Controller::class
                .java
                .methods
                .single { it.name == "getTransactionsByContract" }
                .getAnnotation(CacheFor::class.java)
        assertEquals(CachePolicy.MINUTE, declared?.policy)
    }

    @Test
    fun `contract listing passes cursor, range and direction to the service`() {
        every {
            service.findByContractAddress(address, "5|0x1", 1L, 9L, 50, Direction.ASC, true)
        } returns paginatedResponse(emptyList(), hasNext = false)

        controller.getTransactionsByContract(address, true, 50, "asc", "5|0x1", 1L, 9L)

        verify(exactly = 1) {
            service.findByContractAddress(address, "5|0x1", 1L, 9L, 50, Direction.ASC, true)
        }
    }

    @Test
    fun `an inverted block range is rejected`() {
        assertThrows<BadRequestException> {
            controller.getTransactionsByContract(address, false, null, null, null, 9L, 1L)
        }
    }
}
