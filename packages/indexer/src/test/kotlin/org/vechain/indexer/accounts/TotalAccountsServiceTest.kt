package org.vechain.indexer.accounts

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.accounts.repository.TotalAccountsRepository
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.thor.model.Block

internal class TotalAccountsServiceTest {
    private val repository = mockk<TotalAccountsRepository>(relaxed = true)
    private val inlineVersioningProperties = mockk<InlineVersioningProperties>()
    private val mongoTemplate = mockk<MongoTemplate>(relaxed = true)

    private lateinit var service: TotalAccountsService

    @BeforeEach
    fun setUp() {
        every { inlineVersioningProperties.blockWindow } returns 10000L
        every { inlineVersioningProperties.maxVersions } returns 100
        service = TotalAccountsService(repository, inlineVersioningProperties, mongoTemplate)
    }

    @Test
    fun `createPeriodAccounts uses current block metadata`() {
        val tracker =
            TotalAccounts(
                id = "ALL",
                blockId = "0xold",
                blockNumber = 99L,
                blockTimestamp = 1234567890L,
                total = 10L,
                timeFrame = TimeFrame.ALL,
                dayOfMonth = 1L,
                weekOfYear = 1L,
                month = 1L,
                year = 2025L,
                version = 3,
            )
        val block =
            Block(
                id = "0xnew",
                number = 100L,
                timestamp = 1234567900L,
                parentID = "0xparent",
                size = 0,
                gasLimit = 0,
                baseFeePerGas = null,
                beneficiary = "0xbeneficiary",
                gasUsed = 0,
                totalScore = 0,
                txsRoot = "0xtxs",
                txsFeatures = 0,
                stateRoot = "0xstate",
                receiptsRoot = "0xreceipts",
                signer = "0xsigner",
                isTrunk = true,
                isFinalized = true,
                transactions = emptyList(),
                com = false,
            )

        val result =
            service.createPeriodAccounts("ALL-day-2025-1-1", TimeFrame.DAY, 7L, tracker, block)

        assertThat(result.blockId).isEqualTo(block.id)
        assertThat(result.blockNumber).isEqualTo(block.number)
        assertThat(result.blockTimestamp).isEqualTo(block.timestamp)
        assertThat(result.dayOfMonth).isEqualTo(tracker.dayOfMonth)
        assertThat(result.version).isEqualTo(1)
    }
}
