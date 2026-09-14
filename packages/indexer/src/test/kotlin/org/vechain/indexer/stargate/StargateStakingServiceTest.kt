package org.vechain.indexer.stargate

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import java.math.BigInteger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.staking.NftHoldersByBlock
import org.vechain.indexer.stargate.staking.NftOwnerBalance
import org.vechain.indexer.stargate.staking.StargateStakingService
import org.vechain.indexer.stargate.staking.StargateStakingWriteRepository
import org.vechain.indexer.stargate.staking.VetStakedByBlock
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.stargate.token.TokenLevel.Strength
import org.vechain.indexer.stargate.token.TokenLevel.Thunder
import org.vechain.indexer.timeseries.TimeFramePeriod
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsInt
import org.vechain.indexer.utils.ParamUtils.getAsString
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

@ExtendWith(MockKExtension::class)
class StargateStakingServiceTest {
    @MockK lateinit var repository: StargateStakingWriteRepository

    private lateinit var service: StargateStakingService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        every { repository.latestVetStaked() } returns null
        every { repository.latestNftHolders() } returns null
        every { repository.latestBalancesBefore(any(), any()) } returns emptyList()
        service = StargateStakingService(repository)
    }

    @Test
    fun `processEvents returns an empty update for no events`() {
        expectThat(service.processEvents(emptyList()).isEmpty()).isTrue()
    }

    @Test
    fun `VET staked accumulates per block and per level, with the NFT counts`() {
        val update =
            service.processEvents(
                listOf(
                    stake("b1", 10L, 1000L, "100", Strength),
                    stake("b1", 10L, 1000L, "50", Strength, owner = "0xowner2"),
                    stake("b2", 12L, 1200L, "200", Thunder),
                    unstake("b3", 13L, 1300L, "50", Strength, owner = "0xowner2"),
                )
            )

        val staked = update.vetStaked
        expectThat(staked).hasSize(3)
        expectThat(staked[0]).stakedMatches("b1", 10L, 1000L, BigInteger("150"))
        expectThat(staked[0].blockTotal).isEqualTo(BigInteger("150"))
        expectThat(staked[1]).stakedMatches("b2", 12L, 1200L, BigInteger("350"))
        expectThat(staked[2]).stakedMatches("b3", 13L, 1300L, BigInteger("300"))
        expectThat(staked[2].byLevel)
            .isEqualTo(mapOf(Strength to BigInteger("100"), Thunder to BigInteger("200")))
        expectThat(staked[2].totalNftCount).isEqualTo(2L)
        expectThat(staked[2].nftCountByLevel).isEqualTo(mapOf(Strength to 1L, Thunder to 1L))
    }

    @Test
    fun `continues both series from the persisted records`() {
        every { repository.latestVetStaked() } returns
            staked("prev", 9, 900, BigInteger("500"), mapOf(Strength to BigInteger("500")), 1)
        every { repository.latestNftHolders() } returns
            holders("prev", 9, 900, 5, mapOf(Strength to 5))

        val update = service.processEvents(listOf(stake("b10", 10L, 900L, "50", Strength)))

        expectThat(update.vetStaked.last()).stakedMatches("b10", 10L, 900L, BigInteger("550"))
        expectThat(update.vetStaked.last().totalNftCount).isEqualTo(2L)
        expectThat(update.nftHolders.last())
            .holdersMatches("b10", 10L, 900L, 6, mapOf(Strength to 6))
    }

    @Test
    fun `holders count owners whose balance leaves or reaches zero, overall and per level`() {
        val update =
            service.processEvents(
                listOf(
                    stake("b1", 10, 1000, "1", Strength, "0xowner1"),
                    stake("b2", 12, 1200, "1", Thunder, "0xowner2"),
                    stake("b2", 12, 1200, "1", Thunder, "0xowner1"),
                    unstake("b3", 13, 1300, "1", Strength, "0xowner1"),
                    unstake("b4", 14, 1400, "1", Thunder, "0xowner1"),
                )
            )

        val holders = update.nftHolders
        expectThat(holders).hasSize(4)
        expectThat(holders[0]).holdersMatches("b1", 10, 1000, 1, mapOf(Strength to 1))
        expectThat(holders[1]).holdersMatches("b2", 12, 1200, 2, mapOf(Strength to 1, Thunder to 2))
        expectThat(holders[2]).holdersMatches("b3", 13, 1300, 2, mapOf(Strength to 0, Thunder to 2))
        expectThat(holders[3]).holdersMatches("b4", 14, 1400, 1, mapOf(Strength to 0, Thunder to 1))
        expectThat(holders[3].blockTotal).isEqualTo(BigInteger("-1"))
    }

    @Test
    fun `owner balances are loaded below the first block and written once per owner per block`() {
        every { repository.latestBalancesBefore(setOf("0xowner1", "0xowner2"), 10L) } returns
            listOf(NftOwnerBalance("0xowner1", 2, mapOf(Strength to 1, Thunder to 1), "b8", 8, 800))

        val update =
            service.processEvents(
                listOf(
                    unstake("b1", 10, 1000, "1", Strength, "0xowner1"),
                    stake("b1", 10, 1000, "1", Strength, "0xowner2"),
                    stake("b1", 10, 1000, "1", Thunder, "0xowner2"),
                    unstake("b2", 12, 1200, "1", Thunder, "0xowner1"),
                )
            )

        expectThat(update.ownerBalances)
            .containsExactly(
                NftOwnerBalance("0xowner1", 1, mapOf(Strength to 0, Thunder to 1), "b1", 10, 1000),
                NftOwnerBalance("0xowner2", 2, mapOf(Strength to 1, Thunder to 1), "b1", 10, 1000),
                NftOwnerBalance("0xowner1", 0, mapOf(Strength to 0, Thunder to 0), "b2", 12, 1200),
            )
        // owner1 held two before, so only the second unstake ends their holding.
        expectThat(update.nftHolders.map { it.total }).containsExactly(1L, 0L)
    }

    @Test
    fun `a day rollover re-emits both previous records tagged with the frames that closed`() {
        val first = service.processEvents(listOf(stake("b1", 10, 1735603140, "1", Strength)))
        every { repository.latestVetStaked() } returns first.vetStaked.single()
        every { repository.latestNftHolders() } returns first.nftHolders.single()

        val update = service.processEvents(listOf(stake("b2", 11, 1735603200, "1", Strength)))

        expectThat(update.vetStaked).hasSize(2)
        expectThat(update.vetStaked[0].blockNumber).isEqualTo(10L)
        expectThat(update.vetStaked[0].timeFrames).containsExactly(TimeFrame.HOUR, TimeFrame.DAY)
        expectThat(update.nftHolders[0].timeFrames).containsExactly(TimeFrame.HOUR, TimeFrame.DAY)
        expectThat(update.nftHolders[1].timeFrames).isEmpty()
    }

    @Test
    fun `throws when a block is at or before the newest persisted one on either series`() {
        every { repository.latestNftHolders() } returns
            holders("prev", 10, 900, 1, mapOf(Strength to 1))

        val ex =
            assertThrows<IllegalStateException> {
                service.processEvents(listOf(stake("bad", 10, 1100, "50", Strength)))
            }
        expectThat(ex.message).isEqualTo("Events include block ≤ last persisted block 10")
    }

    @Test
    fun `throws on a missing value, a missing or invalid level, or an unknown event type`() {
        expectThat(
                assertThrows<IllegalStateException> {
                        service.processEvents(listOf(stake("bX", 15, 1500, null, Strength)))
                    }
                    .message
            )
            .isEqualTo("Event for block 15 (blockId=bX) is missing required 'value'")
        expectThat(
                assertThrows<IllegalArgumentException> {
                        service.processEvents(
                            listOf(event("bX", 20, 2000, "10", null, "STARGATE_STAKE"))
                        )
                    }
                    .message
            )
            .isEqualTo("Missing levelId in event params")
        expectThat(
                assertThrows<IllegalArgumentException> {
                        service.processEvents(
                            listOf(event("bX", 15, 1500, "10", 999, "STARGATE_STAKE"))
                        )
                    }
                    .message
            )
            .isEqualTo("Invalid levelId: 999")
        expectThat(
                assertThrows<IllegalArgumentException> {
                        service.processEvents(
                            listOf(event("bX", 22, 2200, "10", Strength.ordinal, "UNKNOWN"))
                        )
                    }
                    .message
            )
            .isEqualTo("Unknown eventType: UNKNOWN")
    }

    @Test
    fun `save writes all three tables through the repository`() {
        val update =
            StargateStakingService.Update(
                listOf(staked("bX", 5, 500, BigInteger.TEN, emptyMap(), 1)),
                listOf(holders("bX", 5, 500, 1, emptyMap())),
                listOf(NftOwnerBalance("0xowner1", 1, mapOf(Strength to 1L), "bX", 5, 500)),
            )
        every { repository.save(update.vetStaked, update.nftHolders, update.ownerBalances) } returns
            Unit

        service.save(update)

        verify(exactly = 1) {
            repository.save(update.vetStaked, update.nftHolders, update.ownerBalances)
        }
    }

    private fun stake(
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        value: String?,
        level: TokenLevel,
        owner: String = "0xowner1",
    ) = event(blockId, blockNumber, blockTimestamp, value, level.ordinal, "STARGATE_STAKE", owner)

    private fun unstake(
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        value: String?,
        level: TokenLevel,
        owner: String = "0xowner1",
    ) = event(blockId, blockNumber, blockTimestamp, value, level.ordinal, "STARGATE_UNSTAKE", owner)

    private fun event(
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        value: String?,
        levelId: Int?,
        eventType: String,
        owner: String = "0xowner1",
    ): IndexedEvent =
        io.mockk.mockk {
            every { this@mockk.blockId } returns blockId
            every { this@mockk.blockNumber } returns blockNumber
            every { this@mockk.blockTimestamp } returns blockTimestamp
            every { this@mockk.eventType } returns eventType
            every { params.getAsBigInteger("value") } returns value?.let { BigInteger(it) }
            every { params.getAsInt("levelId") } returns levelId
            every { params.getAsString("owner") } returns owner
        }

    private fun staked(
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        total: BigInteger,
        byLevel: Map<TokenLevel, BigInteger>,
        nftCount: Long,
    ) =
        VetStakedByBlock(
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            total = total,
            byLevel = byLevel,
            totalNftCount = nftCount,
            nftCountByLevel = byLevel.mapValues { nftCount },
            period = TimeFramePeriod(0, 1, 1, 1, 1970),
        )

    private fun holders(
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        total: Long,
        byLevel: Map<TokenLevel, Long>,
    ) =
        NftHoldersByBlock(
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            total = total,
            byLevel = byLevel,
            period = TimeFramePeriod(0, 1, 1, 1, 1970),
        )

    private fun Assertion.Builder<VetStakedByBlock>.stakedMatches(
        id: String,
        num: Long,
        ts: Long,
        total: BigInteger,
    ) = and {
        get(VetStakedByBlock::blockId).isEqualTo(id)
        get(VetStakedByBlock::blockNumber).isEqualTo(num)
        get(VetStakedByBlock::blockTimestamp).isEqualTo(ts)
        get(VetStakedByBlock::total).isEqualTo(total)
    }

    private fun Assertion.Builder<NftHoldersByBlock>.holdersMatches(
        id: String,
        num: Long,
        ts: Long,
        total: Long,
        byLevel: Map<TokenLevel, Long>,
    ) = and {
        get(NftHoldersByBlock::blockId).isEqualTo(id)
        get(NftHoldersByBlock::blockNumber).isEqualTo(num)
        get(NftHoldersByBlock::blockTimestamp).isEqualTo(ts)
        get(NftHoldersByBlock::total).isEqualTo(total)
        get(NftHoldersByBlock::byLevel).isEqualTo(byLevel)
    }
}
