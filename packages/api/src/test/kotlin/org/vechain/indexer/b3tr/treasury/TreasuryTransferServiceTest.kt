package org.vechain.indexer.b3tr.treasury

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class TreasuryTransferServiceTest {

    private val repository: TreasuryTransferReadRepository = mockk()
    private val service = TreasuryTransferService(repository)

    private val pageable =
        PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "blockTimestamp", "txId", "_id"))

    @Test
    fun `the filters and the page reach the repository, and a full page reports one more`() {
        every {
            repository.find(TreasuryTransferCategory.OUT, 500L, 900L, 0, 3, Sort.Direction.DESC)
        } returns listOf(transfer("t1"), transfer("t2"), transfer("t3"))

        val result =
            service.find(
                category = TreasuryTransferCategory.OUT,
                after = 500L,
                before = 900L,
                pageable = pageable,
            )

        expectThat(result.content).hasSize(2)
        expectThat(result.hasNext()).isTrue()
    }

    @Test
    fun `a short page is the last one`() {
        every { repository.find(null, null, null, 0, 3, Sort.Direction.DESC) } returns
            listOf(transfer("t1"))

        val result = service.find(pageable = pageable)

        expectThat(result.content).hasSize(1)
        expectThat(result.hasNext()).isFalse()
    }

    @Test
    fun `the latest indexed block comes from the newest row`() {
        every { repository.latestBlockNumber() } returns 100L

        expectThat(service.getLatestIndexedBlocks()["TreasuryTransfer"]).isEqualTo(100L)
    }

    private fun transfer(id: String) =
        TreasuryTransfer(
            id = id,
            blockId = "0xblock",
            blockNumber = 100L,
            blockTimestamp = 1000L,
            txId = "0xtx-$id",
            from = "0xfrom",
            to = "0xto",
            value = "1000000000000000000",
            category = TreasuryTransferCategory.OTHER,
            label = "test",
            counterpartyName = null,
        )
}
