package org.vechain.indexer.validators

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction.ASC
import org.springframework.data.domain.Sort.Direction.DESC
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.prices.PriceFeed
import org.vechain.indexer.prices.PriceFeedService
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.timeseries.TimeSeriesResolution
import org.vechain.indexer.validator.BlockStatus
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorBlock
import org.vechain.indexer.validator.ValidatorBlockReadRepository
import org.vechain.indexer.validator.ValidatorReadRepository
import org.vechain.indexer.validator.ValidatorSlotStats
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNull
import strikt.assertions.isTrue

class ValidatorServiceTest {
    private val validatorBlockRepository: ValidatorBlockReadRepository = mockk()
    private val validatorRepository: ValidatorReadRepository = mockk()
    private val aggregateService: ValidatorAggregateService = mockk {
        every { build(any()) } returns
            ValidatorAggregates(
                totalWeight = BigDecimal.ZERO,
                totalNextPeriodWeight = BigDecimal.ZERO,
                totalActiveVetStaked = BigDecimal.ZERO,
                totalActiveNextCycleVetStaked = BigDecimal.ZERO,
                delegationFacetsByValidator = emptyMap(),
            )
    }
    private val priceFeedService: PriceFeedService = mockk {
        every { getPrices(any()) } returns
            mapOf(PriceFeed.VET_USD to BigDecimal.ZERO, PriceFeed.VTHO_USD to BigDecimal.ZERO)
    }
    private val thorClient: ThorClient = mockk()

    private val service =
        ValidatorService(
            validatorBlockRepository = validatorBlockRepository,
            validatorRepository = validatorRepository,
            aggregateService = aggregateService,
            priceFeedService = priceFeedService,
            thorClient = thorClient,
        )

    private val validator = "0x1234567890abcdef1234567890abcdef12345678"
    private val byBlock = Sort.by(DESC, "blockNumber")

    // -- getValidatorHistoricBlocks --

    @Test
    fun `getValidatorHistoricBlocks includes boundary records for sampled ranges`() {
        val startBoundary = validatorBlock(blockNumber = 90)
        val sampled = listOf(validatorBlock(blockNumber = 360))
        val endBoundary = validatorBlock(blockNumber = 600)

        every {
            validatorBlockRepository.findValidatedInRange(
                validator,
                1_000L,
                6_000L,
                TimeSeriesResolution.HOURLY,
            )
        } returns sampled
        every { validatorBlockRepository.findLatestValidatedAtOrBefore(validator, 1_000L) } returns
            startBoundary
        every { validatorBlockRepository.findLatestValidatedAtOrBefore(validator, 6_000L) } returns
            endBoundary

        val result = service.getValidatorHistoricBlocks(1_000L, 6_000L, validator)

        expectThat(result.map { it.blockTimestamp }).isEqualTo(listOf(900L, 3_600L, 6_000L))
    }

    @Test
    fun `getValidatorHistoricBlocks reads every row for a short range and samples a long one`() {
        every {
            validatorBlockRepository.findValidatedInRange(validator, any(), any(), any())
        } returns emptyList()
        every { validatorBlockRepository.findLatestValidatedAtOrBefore(validator, any()) } returns
            null

        service.getValidatorHistoricBlocks(0L, 3_000L, validator)
        service.getValidatorHistoricBlocks(0L, 40_000_000L, validator)

        verify(exactly = 1) {
            validatorBlockRepository.findValidatedInRange(
                validator,
                0L,
                3_000L,
                TimeSeriesResolution.RAW,
            )
        }
        verify(exactly = 1) {
            validatorBlockRepository.findValidatedInRange(
                validator,
                0L,
                40_000_000L,
                TimeSeriesResolution.MONTHLY,
            )
        }
    }

    @Test
    fun `getValidatorHistoricBlocks rejects oversized start timestamp`() {
        val exception =
            assertThrows<BadRequestException> {
                service.getValidatorHistoricBlocks(
                    31_556_889_832_694_401L,
                    31_556_889_832_694_401L,
                    validator,
                )
            }

        expectThat(exception.message)
            .isEqualTo("Invalid 'startTimestamp' timestamp: exceeds supported Unix timestamp range")
    }

    // -- getValidatorBlockRewards --

    @Test
    fun `getValidatorBlockRewards passes the filters, the direction and one row past the page`() {
        every {
            validatorBlockRepository.findRewards(any(), any(), any(), any(), any(), any())
        } returns emptyList()

        service.getValidatorBlockRewards(null, null, null, PageRequest.of(0, 10, byBlock))
        service.getValidatorBlockRewards(
            Address(validator),
            500L,
            BlockStatus.VALIDATED,
            PageRequest.of(2, 5, Sort.by(ASC, "blockNumber")),
        )

        verify { validatorBlockRepository.findRewards(null, null, null, DESC, 0, 11) }
        verify {
            validatorBlockRepository.findRewards(validator, 500L, BlockStatus.VALIDATED, ASC, 10, 6)
        }
    }

    @Test
    fun `getValidatorBlockRewards hasNext is set only by the extra row`() {
        val pageable = PageRequest.of(0, 3, byBlock)
        every { validatorBlockRepository.findRewards(null, null, null, DESC, 0, 4) } returnsMany
            listOf(
                (1..3).map { validatorBlock(blockNumber = it.toLong()) },
                (1..4).map { validatorBlock(blockNumber = it.toLong()) },
                (1..2).map { validatorBlock(blockNumber = it.toLong()) },
            )

        val exact = service.getValidatorBlockRewards(null, null, null, pageable)
        val more = service.getValidatorBlockRewards(null, null, null, pageable)
        val fewer = service.getValidatorBlockRewards(null, null, null, pageable)

        expectThat(exact.data).hasSize(3)
        expectThat(exact.pagination.hasNext).isFalse()
        expectThat(more.data).hasSize(3)
        expectThat(more.pagination.hasNext).isTrue()
        expectThat(fewer.data).hasSize(2)
        expectThat(fewer.pagination.hasNext).isFalse()
    }

    // -- getBlockByNumber --

    @Test
    fun `getBlockByNumber forwards the block and the optional validator`() {
        val rows =
            listOf(
                validatorBlock(blockNumber = 12345, validator = "0xaaa"),
                validatorBlock(
                    blockNumber = 12345,
                    status = BlockStatus.MISSED,
                    validator = "0xbbb",
                ),
            )
        every { validatorBlockRepository.findByBlockNumber(12345, null) } returns rows
        every { validatorBlockRepository.findByBlockNumber(12345, validator) } returns rows.take(1)
        every { validatorBlockRepository.findByBlockNumber(99999, null) } returns emptyList()

        expectThat(service.getBlockByNumber(12345, null)).hasSize(2)
        expectThat(service.getBlockByNumber(12345, Address(validator))).hasSize(1)
        expectThat(service.getBlockByNumber(99999, null)).isEmpty()
    }

    // -- getValidators --

    @Test
    fun `getValidators hasNext is true and content is trimmed only when an extra row comes back`() {
        val pageable = PageRequest.of(0, 3, Sort.by(DESC, "validatorVetStaked"))
        every {
            validatorRepository.find(any(), any(), any(), any(), any(), any(), any())
        } returnsMany
            listOf(
                (1..3).map { validator(id = "0x000000000000000000000000000000000000000$it") },
                (1..4).map { validator(id = "0x000000000000000000000000000000000000000$it") },
                (1..2).map { validator(id = "0x000000000000000000000000000000000000000$it") },
            )

        val exact = service.getValidators(null, null, null, pageable)
        val more = service.getValidators(null, null, null, pageable)
        val fewer = service.getValidators(null, null, null, pageable)

        expectThat(exact.content).hasSize(3)
        expectThat(exact.hasNext()).isFalse()
        expectThat(more.content).hasSize(3)
        expectThat(more.hasNext()).isTrue()
        expectThat(fewer.content).hasSize(2)
        expectThat(fewer.hasNext()).isFalse()
        verify(exactly = 3) {
            validatorRepository.find(null, null, null, "validatorVetStaked", DESC, 0, 4)
        }
    }

    // -- slot stats --

    @Test
    fun `getSlotStats forwards the timestamp window and rejects an inverted one`() {
        every { validatorBlockRepository.slotStats(1_000L, 2_000L) } returns emptyList()

        expectThat(service.getSlotStats(1_000L, 2_000L)).isEmpty()
        assertThrows<BadRequestException> { service.getSlotStats(2_000L, 1_000L) }
    }

    @Test
    fun `getSlotStatsForValidator returns the single row or null`() {
        val stats = ValidatorSlotStats("0xabc", 10, 2, 2.0 / 12, 0.9)
        every { validatorBlockRepository.slotStats(1_000L, 2_000L, "0xabc") } returns listOf(stats)
        every { validatorBlockRepository.slotStats(1_000L, 2_000L, "0xdef") } returns emptyList()

        expectThat(service.getSlotStatsForValidator(1_000L, 2_000L, "0xabc")).isEqualTo(stats)
        expectThat(service.getSlotStatsForValidator(1_000L, 2_000L, "0xdef")).isNull()
    }

    private fun validator(id: String): Validator =
        Validator(id = id, blockId = "0xblock1", blockNumber = 1, blockTimestamp = 10)

    private fun validatorBlock(
        blockNumber: Long,
        status: BlockStatus = BlockStatus.VALIDATED,
        validator: String = "0xdefault",
    ): ValidatorBlock =
        ValidatorBlock(
            id = "id-$blockNumber-$validator",
            blockId = "0xblock$blockNumber",
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 10,
            validator = validator,
            blockReward = BigInteger("1000"),
            priorityReward = BigInteger("100"),
            total = BigInteger("1100"),
            status = status,
        )
}
