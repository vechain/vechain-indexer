package org.vechain.indexer.history

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.history.HistoryReadRepository.SearchField
import org.vechain.indexer.thor.Address

class HistoryServiceTest {
    private val repository: HistoryReadRepository = mockk()
    private val service = HistoryService(repository)
    private val account = "0x" + "a".repeat(40)
    private val contract = Address("0x" + "c".repeat(40))

    private fun event(id: String) =
        IndexedHistoryEvent(id, "0x01", 1, 10, "0x02", eventName = HistoryEventName.TRANSFER_VET)

    @Test
    fun `without searchBy the account read pages one row past the page`() {
        val pageable = PageRequest.of(1, 2, Sort.by(Direction.ASC, "blockTimestamp"))
        every {
            repository.findByAccount(
                account,
                listOf("TRANSFER_VET"),
                contract.value,
                5L,
                9L,
                2L,
                3,
                Direction.ASC,
            )
        } returns listOf(event("1"), event("2"), event("3"))

        val slice =
            service.findUserHistoryByFilters(
                account,
                listOf("TRANSFER_VET"),
                emptyList(),
                contract,
                9L,
                5L,
                pageable,
            )

        assertEquals(listOf("1", "2"), slice.content.map { it.id })
        assertTrue(slice.hasNext())
    }

    @Test
    fun `searchBy names the fields of the union`() {
        val pageable = PageRequest.of(0, 20, Sort.by(Direction.DESC, "blockTimestamp"))
        every {
            repository.findBySearchFields(
                account,
                listOf(SearchField.TO, SearchField.GAS_PAYER),
                null,
                null,
                null,
                null,
                0L,
                21,
                Direction.DESC,
            )
        } returns listOf(event("1"))

        val slice =
            service.findUserHistoryByFilters(
                account,
                null,
                listOf("to", "gasPayer"),
                null,
                null,
                null,
                pageable,
            )

        assertEquals(listOf("1"), slice.content.map { it.id })
        assertTrue(!slice.hasNext())
    }
}
