package org.vechain.indexer.stargate

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByAccount
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByAccountArchive
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByAccountRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByAccountService
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString
import strikt.api.expect
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
internal class VthoClaimByAccountServiceTest {
    @MockK lateinit var repository: VthoClaimedByAccountRepository

    @MockK
    lateinit var archiveService: ArchiveService<VthoClaimedByAccount, VthoClaimedByAccountArchive>

    @MockK lateinit var pruner: TargetedPruner<VthoClaimedByAccount, VthoClaimedByAccountArchive>

    private lateinit var service: VthoClaimedByAccountService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = VthoClaimedByAccountService(repository, archiveService, pruner)
    }

    @Test
    fun `parseRecords returns empty list for empty events`() {
        val result = service.parseRecords(emptyList(), emptyList())
        expect { that(result.size).isEqualTo(0) }
    }

    @Test
    fun `parseRecords creates new records for new accounts`() {
        val events =
            listOf(
                mockk<IndexedEvent> {
                    every { blockId } returns "block1"
                    every { blockNumber } returns 10L
                    every { blockTimestamp } returns 1000L
                    every { params.getAsString("owner") } returns "0xabc"
                    every { params.getAsBigInteger("value") } returns BigInteger("100")
                    every { eventType } returns "STARGATE_CLAIM_REWARDS"
                },
                mockk<IndexedEvent> {
                    every { blockId } returns "block2"
                    every { blockNumber } returns 12L
                    every { blockTimestamp } returns 1200L
                    every { params.getAsString("owner") } returns "0xabc"
                    every { params.getAsBigInteger("value") } returns BigInteger("200")
                    every { eventType } returns "STARGATE_CLAIM_REWARDS"
                },
                mockk<IndexedEvent> {
                    every { blockId } returns "block3"
                    every { blockNumber } returns 13L
                    every { blockTimestamp } returns 1300L
                    every { params.getAsString("owner") } returns "0xdef"
                    every { params.getAsBigInteger("value") } returns BigInteger("50")
                    every { eventType } returns "STARGATE_CLAIM_REWARDS"
                },
            )
        val result = service.parseRecords(events, emptyList())
        expect {
            that(result.size).isEqualTo(2)
            val abc = result.find { it.account == "0xabc" }
            val def = result.find { it.account == "0xdef" }
            that(abc?.total).isEqualTo(BigInteger("300"))
            that(abc?.version).isEqualTo(1)
            that(abc?.blockId).isEqualTo("block3")
            that(abc?.blockNumber).isEqualTo(13L)
            that(abc?.blockTimestamp).isEqualTo(1300L)
            that(def?.total).isEqualTo(BigInteger("50"))
            that(def?.version).isEqualTo(1)
        }
    }

    @Test
    fun `parseRecords increments version and adds to previous total`() {
        val events =
            listOf(
                mockk<IndexedEvent> {
                    every { blockId } returns "block4"
                    every { blockNumber } returns 20L
                    every { blockTimestamp } returns 2000L
                    every { params.getAsString("owner") } returns "0xabc"
                    every { params.getAsBigInteger("value") } returns BigInteger("10")
                    every { eventType } returns "STARGATE_CLAIM_REWARDS"
                }
            )
        val existing =
            listOf(
                VthoClaimedByAccount(
                    blockId = "block3",
                    blockNumber = 13L,
                    blockTimestamp = 1300L,
                    total = BigInteger("300"),
                    account = "0xabc",
                    version = 1,
                    legacyRewards = BigInteger.ZERO,
                    delegationRewards = BigInteger("300"),
                )
            )
        val result = service.parseRecords(events, existing)
        expect {
            that(result.size).isEqualTo(1)
            that(result[0].account).isEqualTo("0xabc")
            that(result[0].total).isEqualTo(BigInteger("310"))
            that(result[0].version).isEqualTo(2)
            that(result[0].blockId).isEqualTo("block4")
            that(result[0].blockNumber).isEqualTo(20L)
            that(result[0].blockTimestamp).isEqualTo(2000L)
        }
    }

    @Test
    fun `update saves update and archives existing`() {
        val update =
            listOf(
                VthoClaimedByAccount(
                    1,
                    "block1",
                    1L,
                    1000L,
                    BigInteger("1"),
                    BigInteger("0"),
                    BigInteger("1"),
                    "0xabc",
                )
            )
        val existing =
            listOf(
                VthoClaimedByAccount(
                    0,
                    "block0",
                    0L,
                    900L,
                    BigInteger("0"),
                    BigInteger("0"),
                    BigInteger("0"),
                    "0xabc",
                )
            )
        every { repository.saveAll(update) } returns update
        every { archiveService.saveAll(existing) } just Runs

        service.save(update, existing)

        verify(exactly = 1) { repository.saveAll(update) }
        verify(exactly = 1) { archiveService.saveAll(existing) }
    }

    @Test
    fun `update does not save or archive empty lists`() {
        val update = emptyList<VthoClaimedByAccount>()
        val existing = emptyList<VthoClaimedByAccount>()
        every { repository.saveAll(update) } returns update
        every { archiveService.saveAll(existing) } just Runs

        service.save(update, existing)

        verify(exactly = 0) { repository.saveAll(update) }
        verify(exactly = 0) { archiveService.saveAll(existing) }
    }

    @Test
    fun `getExisting fetches by owner addresses`() {
        val events =
            listOf(
                mockk<IndexedEvent> { every { params.getAsString("owner") } returns "0xabc" },
                mockk<IndexedEvent> { every { params.getAsString("owner") } returns "0xdef" },
            )
        val existing =
            listOf(
                VthoClaimedByAccount(
                    1,
                    "block1",
                    1L,
                    1000L,
                    BigInteger("1"),
                    BigInteger("0"),
                    BigInteger("1"),
                    "0xabc",
                )
            )
        every { repository.findAllById(listOf("0xabc", "0xdef")) } returns existing

        val result = service.getExisting(events)

        verify(exactly = 1) { repository.findAllById(listOf("0xabc", "0xdef")) }
        expect {
            that(result.size).isEqualTo(1)
            that(result[0].account).isEqualTo("0xabc")
        }
    }
}
