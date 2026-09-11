package org.vechain.indexer.stargate

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.history.HistoryEventName
import org.vechain.indexer.history.HistoryReadRepository
import org.vechain.indexer.history.IndexedHistoryEvent
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class StargateTokenHistoryServiceTest {
    private val repository: HistoryReadRepository = mockk()
    private val stargateNftContract = "0x1856c533ac2d94340aaa8544d35a5c1d4a21dee7"
    private val service =
        StargateTokenHistoryService(repository, stargateNftContract.uppercase().replace("0X", "0x"))

    @Test
    fun `findTokenHistory scopes to the protocol and the stargate contract and normalises the token id`() {
        val pageable = PageRequest.of(0, 2, Sort.by(Sort.Order.desc("blockTimestamp")))
        val first = historyEvent("event-1", HistoryEventName.STARGATE_STAKE, 300)
        val second = historyEvent("event-2", HistoryEventName.NFT_SALE, 200)
        val third = historyEvent("event-3", HistoryEventName.TRANSFER_NFT, 100)
        val protocol = slot<List<String>>()
        val nft = slot<List<String>>()
        every {
            repository.findStargateTokenHistory(
                "42",
                null,
                capture(protocol),
                capture(nft),
                stargateNftContract,
                100,
                500,
                0L,
                3,
                Direction.DESC,
            )
        } returns listOf(first, second, third)

        val result =
            service.findTokenHistory(
                "0x2a",
                eventNames = null,
                before = 500,
                after = 100,
                pageable = pageable,
            )

        expectThat(protocol.captured)
            .contains("STARGATE_STAKE", "STARGATE_UNSTAKE", "STARGATE_CLAIM_REWARDS")
        expectThat(nft.captured).containsExactly("TRANSFER_NFT", "NFT_SALE", "VEVOTE_VOTE_CAST")
        expectThat(result.content).containsExactly(first, second)
        expectThat(result.hasNext()).isTrue()
    }

    @Test
    fun `findTokenHistory forwards an explicit event filter and treats an empty one as none`() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("blockTimestamp")))
        val names = slot<List<String>?>()
        every {
            repository.findStargateTokenHistory(
                "42",
                captureNullable(names),
                any(),
                any(),
                any(),
                null,
                null,
                0L,
                11,
                Direction.DESC,
            )
        } returns emptyList()

        service.findTokenHistory(
            "42",
            listOf("STARGATE_CLAIM_REWARDS", "NFT_SALE"),
            null,
            null,
            pageable,
        )
        expectThat(names.captured).isEqualTo(listOf("STARGATE_CLAIM_REWARDS", "NFT_SALE"))

        val result = service.findTokenHistory("42", emptyList(), null, null, pageable)
        expectThat(names.captured).isEqualTo(null)
        expectThat(result.hasNext()).isFalse()
    }

    private fun historyEvent(id: String, eventName: HistoryEventName, blockTimestamp: Long) =
        IndexedHistoryEvent(
            id = id,
            blockId = "block-$id",
            blockNumber = blockTimestamp,
            blockTimestamp = blockTimestamp,
            txId = "tx-$id",
            contractAddress = stargateNftContract,
            tokenId = "42",
            eventName = eventName,
        )
}
