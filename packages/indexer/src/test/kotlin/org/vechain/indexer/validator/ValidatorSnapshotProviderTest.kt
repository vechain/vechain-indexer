package org.vechain.indexer.validator

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ValidatorSnapshotProviderTest {
    private val repository = mockk<ValidatorRepository>()
    private val provider = ValidatorSnapshotProvider(repository)

    @Test
    fun `snapshotsForBlock reuses full snapshot between epoch boundaries`() {
        every { repository.findByStatusNot(Status.WITHDRAWN) } returns
            listOf(validator("0xvalidator", startBlock = 180L))

        val first = provider.snapshotsForBlock(181L)
        val second = provider.snapshotsForBlock(182L)

        assertThat(first.refreshed).isTrue()
        assertThat(second.refreshed).isFalse()
        assertThat(second.snapshots["0xvalidator"]?.startBlock).isEqualTo(180L)
        verify(exactly = 1) { repository.findByStatusNot(Status.WITHDRAWN) }
    }

    @Test
    fun `snapshotsForBlock refreshes full snapshot on epoch boundary`() {
        every { repository.findByStatusNot(Status.WITHDRAWN) } returnsMany
            listOf(
                listOf(validator("0xvalidator", startBlock = 180L)),
                listOf(validator("0xvalidator", startBlock = 360L)),
            )

        provider.snapshotsForBlock(181L)
        val epoch = provider.snapshotsForBlock(360L)

        assertThat(epoch.refreshed).isTrue()
        assertThat(epoch.snapshots["0xvalidator"]?.startBlock).isEqualTo(360L)
        verify(exactly = 2) { repository.findByStatusNot(Status.WITHDRAWN) }
    }

    @Test
    fun `snapshotForValidator performs targeted lookup when validator is not in cached snapshot`() {
        every { repository.findByStatusNot(Status.WITHDRAWN) } returns emptyList()
        every { repository.findById("0xvalidator") } returns
            java.util.Optional.of(validator("0xvalidator", startBlock = 180L))

        provider.snapshotsForBlock(181L)
        val snapshot = provider.snapshotForValidator("0xvalidator", 181L)

        assertThat(snapshot?.startBlock).isEqualTo(180L)
        verify(exactly = 1) { repository.findByStatusNot(Status.WITHDRAWN) }
        verify(exactly = 1) { repository.findById("0xvalidator") }
    }

    private fun validator(id: String, startBlock: Long) =
        Validator(
            id = id,
            blockId = "0xblock",
            blockNumber = startBlock,
            blockTimestamp = 1000L,
            status = Status.ACTIVE,
            cyclePeriodLength = 180L,
            startBlock = startBlock,
        )
}
