@file:Suppress(
    "DEPRECATION"
) // V1 (deprecated) wire surface backed by V2 data + V1-only block-rewards helpers.

package org.vechain.indexer.validators

import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.stereotype.Service
import org.vechain.indexer.prices.PriceFeed
import org.vechain.indexer.prices.PriceFeedService
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.timeseries.TimeSeriesResolution
import org.vechain.indexer.utils.PaginationUtils.offsetSlice
import org.vechain.indexer.utils.TimeSeriesUtils
import org.vechain.indexer.utils.TimeValidationUtils
import org.vechain.indexer.validator.BlockStatus
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorBlock
import org.vechain.indexer.validator.ValidatorBlockReadRepository
import org.vechain.indexer.validator.ValidatorReadRepository
import org.vechain.indexer.validator.ValidatorSlotStats

@Profile("validator")
@Service
open class ValidatorService(
    private val validatorBlockRepository: ValidatorBlockReadRepository,
    private val validatorRepository: ValidatorReadRepository,
    private val aggregateService: ValidatorAggregateService,
    private val priceFeedService: PriceFeedService,
    private val thorClient: ThorClient,
) {

    open fun getValidatorBlockRewards(
        validator: Address?,
        blockNumber: Long?,
        status: BlockStatus?,
        pageable: Pageable,
    ): PaginatedResponse<ValidatorBlock> =
        paginatedResponse(
            offsetSlice(pageable, ValidatorBlock::blockNumber.name) { offset, limit, direction ->
                validatorBlockRepository.findRewards(
                    validator?.value,
                    blockNumber,
                    status,
                    direction,
                    offset,
                    limit,
                )
            }
        )

    open fun getBlockByNumber(blockNumber: Long, validator: Address?): List<ValidatorBlock> =
        validatorBlockRepository.findByBlockNumber(blockNumber, validator?.value)

    /**
     * Retrieves block rewards data for a given timestamp range. The granularity of the data is
     * automatically determined based on the size of the time range to optimize for reasonable data
     * point counts.
     *
     * Granularity rules:
     * - Range <= 1 hour: All blocks (~360 data points)
     * - Range <= 1 week: Hourly aggregates (~168 data points)
     * - Range <= 1 month: Daily aggregates (~30 data points)
     * - Range <= 1 year: Weekly aggregates (~52 data points)
     * - Range > 1 year: Monthly aggregates
     *
     * @param startTimestamp The starting timestamp in seconds (inclusive)
     * @param endTimestamp The ending timestamp in seconds (inclusive)
     * @return List of Valid records matching the criteria
     */
    open fun getValidatorHistoricBlocks(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock> {
        TimeValidationUtils.validateTimestamps(
            startTimestamp,
            endTimestamp,
            "startTimestamp",
            "endTimestamp",
        )

        val resolution = TimeSeriesUtils.selectResolution(endTimestamp - startTimestamp)
        val inRange = { start: Long, end: Long ->
            validatorBlockRepository.findValidatedInRange(validator, start, end, resolution)
        }
        if (resolution == TimeSeriesResolution.RAW) return inRange(startTimestamp, endTimestamp)
        return TimeSeriesUtils.getBookendedRecords(startTimestamp, endTimestamp, inRange) {
            validatorBlockRepository.findLatestValidatedAtOrBefore(validator, it)
        }
    }

    /** One page of the current set, in the pageable's first non-id order. */
    open fun findValidators(
        validatorId: String?,
        endorser: String?,
        statuses: List<Status>?,
        pageable: Pageable,
    ): Slice<Validator> {
        val sortField =
            pageable.sort.firstOrNull { it.property != "_id" }?.property
                ?: Validator::validatorVetStaked.name
        return offsetSlice(pageable, sortField) { offset, limit, direction ->
            validatorRepository.find(
                validatorId,
                endorser,
                statuses,
                sortField,
                direction,
                offset,
                limit,
            )
        }
    }

    open fun findValidator(validatorId: String): Validator? =
        validatorRepository.findById(validatorId)

    open fun getValidators(
        validatorId: String?,
        endorser: String?,
        statuses: List<Status>?,
        pageable: Pageable,
    ): Slice<ValidatorResponse> {
        val page = findValidators(validatorId, endorser, statuses, pageable)

        // Empty pages don't need price data — skip the oracle hop so a no-match query never
        // returns 503 just because the oracle happens to be flaky.
        if (page.isEmpty) {
            return SliceImpl(emptyList(), pageable, page.hasNext())
        }

        val aggregates = aggregateService.build(page.content.map { it.id })
        val prices = priceFeedService.getPrices(setOf(PriceFeed.VET_USD, PriceFeed.VTHO_USD))
        val vetPrice = prices.getValue(PriceFeed.VET_USD)
        val vthoPrice = prices.getValue(PriceFeed.VTHO_USD)
        val mapped =
            page.content.map { ValidatorResponse.from(it, aggregates, vetPrice, vthoPrice) }

        return SliceImpl(mapped, pageable, page.hasNext())
    }

    open fun getSlotStats(startTimestamp: Long, endTimestamp: Long): List<ValidatorSlotStats> {
        TimeValidationUtils.validateTimestamps(
            startTimestamp,
            endTimestamp,
            "startTimestamp",
            "endTimestamp",
        )
        return validatorBlockRepository.slotStats(startTimestamp, endTimestamp)
    }

    open fun getSlotStatsForValidator(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): ValidatorSlotStats? {
        TimeValidationUtils.validateTimestamps(
            startTimestamp,
            endTimestamp,
            "startTimestamp",
            "endTimestamp",
        )
        return validatorBlockRepository
            .slotStats(startTimestamp, endTimestamp, validator)
            .firstOrNull()
    }

    open fun getCurrentBlockNumber(): Long =
        runBlocking { thorClient.getBlock(BlockRevision.Keyword.BEST) }.number
}
