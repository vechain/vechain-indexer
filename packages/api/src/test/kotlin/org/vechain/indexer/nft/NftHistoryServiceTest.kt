package org.vechain.indexer.nft

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.history.HistoryEventName
import org.vechain.indexer.history.HistoryReadRepository
import org.vechain.indexer.history.IndexedHistoryEvent
import org.vechain.indexer.thor.Address
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class NftHistoryServiceTest {
    private val repository: HistoryReadRepository = mockk()
    private val service = NftHistoryService(repository)
    private val contractAddress = Address("0x1856c533ac2d94340aaa8544d35a5c1d4a21dee7")

    @Test
    fun `findTokenHistory normalises the token id and defaults to transfers and sales`() {
        val pageable = PageRequest.of(0, 2, Sort.by(Sort.Order.desc("blockTimestamp")))
        val first = historyEvent("event-1", HistoryEventName.NFT_SALE, 300)
        val second = historyEvent("event-2", HistoryEventName.TRANSFER_NFT, 200)
        val third = historyEvent("event-3", HistoryEventName.NFT_SALE, 100)
        every {
            repository.findTokenHistory(
                contractAddress.value,
                "42",
                listOf("TRANSFER_NFT", "NFT_SALE"),
                100,
                500,
                0L,
                3,
                Direction.DESC,
            )
        } returns listOf(first, second, third)

        val result =
            service.findTokenHistory(
                contractAddress = contractAddress,
                tokenId = "0x2a",
                eventNames = null,
                before = 500,
                after = 100,
                pageable = pageable,
            )

        expectThat(result.content).containsExactly(first, second)
        expectThat(result.hasNext()).isTrue()
    }

    @Test
    fun `findTokenHistory applies an explicit event filter and reports no next page`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.asc("blockTimestamp")))
        val only = historyEvent("event-1", HistoryEventName.NFT_SALE, 100)
        every {
            repository.findTokenHistory(
                contractAddress.value,
                "42",
                listOf("NFT_SALE"),
                null,
                null,
                0L,
                11,
                Direction.ASC,
            )
        } returns listOf(only)

        val result =
            service.findTokenHistory(
                contractAddress = contractAddress,
                tokenId = "42",
                eventNames = listOf("NFT_SALE"),
                before = null,
                after = null,
                pageable = pageable,
            )

        expectThat(result.content).containsExactly(only)
        expectThat(result.hasNext()).isFalse()
    }

    private fun historyEvent(id: String, eventName: HistoryEventName, blockTimestamp: Long) =
        IndexedHistoryEvent(
            id = id,
            blockId = "block-$id",
            blockNumber = blockTimestamp,
            blockTimestamp = blockTimestamp,
            txId = "tx-$id",
            contractAddress = contractAddress.value,
            tokenId = "42",
            eventName = eventName,
        )
}
