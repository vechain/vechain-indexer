@file:Suppress(
    "DEPRECATION"
) // V1 (deprecated) wire surface backed by V2 data + V1-only block-rewards helpers.

package org.vechain.indexer.validators

import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.vechain.indexer.prices.PriceFeed
import org.vechain.indexer.prices.PriceFeedService
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.timeseries.TimeSeriesResolution
import org.vechain.indexer.utils.TimeSeriesUtils
import org.vechain.indexer.utils.TimeValidationUtils
import org.vechain.indexer.validator.BlockStatus
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorBlock
import org.vechain.indexer.validator.ValidatorBlockRepository
import org.vechain.indexer.validator.ValidatorSlotStats

@Profile("validator")
@Service
open class ValidatorService(
    private val validatorBlockRepository: ValidatorBlockRepository,
    private val mongoTemplate: MongoTemplate,
    private val aggregateService: ValidatorAggregateService,
    private val priceFeedService: PriceFeedService,
    private val thorClient: ThorClient,
) {

    open fun getValidatorBlocks(
        validator: Address?,
        status: BlockStatus?,
        blockNumber: Long?,
        pageable: Pageable,
    ): PaginatedResponse<ValidatorBlock> {
        val criteriaList = mutableListOf<Criteria>()

        validator?.let {
            criteriaList.add(
                Criteria.where(ValidatorBlock::validator.name).`is`(it.value.lowercase())
            )
        }
        status?.let { criteriaList.add(Criteria.where(ValidatorBlock::status.name).`is`(it)) }
        blockNumber?.let {
            criteriaList.add(Criteria.where(ValidatorBlock::blockNumber.name).`is`(it))
        }

        val query =
            if (criteriaList.isNotEmpty()) {
                Query(Criteria().andOperator(*criteriaList.toTypedArray()))
            } else {
                Query()
            }

        query.with(pageable).limit(pageable.pageSize + 1)

        val results = mongoTemplate.find<ValidatorBlock>(query)
        val hasNext = results.size > pageable.pageSize
        val page = if (hasNext) results.dropLast(1) else results
        val slice = SliceImpl(page, pageable, hasNext)

        return paginatedResponse(slice)
    }

    open fun getValidatorBlockRewards(
        validator: Address?,
        blockNumber: Long?,
        status: BlockStatus?,
        pageable: Pageable,
    ): PaginatedResponse<ValidatorBlock> {
        val criteriaList = mutableListOf<Criteria>()

        validator?.let {
            criteriaList.add(
                Criteria.where(ValidatorBlock::validator.name).`is`(it.value.lowercase())
            )
        }
        blockNumber?.let {
            val isAscending =
                pageable.sort.getOrderFor(ValidatorBlock::blockNumber.name)?.isAscending ?: false
            if (isAscending) {
                criteriaList.add(Criteria.where(ValidatorBlock::blockNumber.name).gte(it))
            } else {
                criteriaList.add(Criteria.where(ValidatorBlock::blockNumber.name).lte(it))
            }
        }
        status?.let { criteriaList.add(Criteria.where(ValidatorBlock::status.name).`is`(it)) }

        val query =
            if (criteriaList.isNotEmpty()) {
                Query(Criteria().andOperator(*criteriaList.toTypedArray()))
            } else {
                Query()
            }

        query.with(pageable).limit(pageable.pageSize + 1)

        val results = mongoTemplate.find<ValidatorBlock>(query)
        val hasNext = results.size > pageable.pageSize
        val page = if (hasNext) results.dropLast(1) else results
        val slice = SliceImpl(page, pageable, hasNext)

        return paginatedResponse(slice)
    }

    open fun getBlockByNumber(blockNumber: Long, validator: Address?): List<ValidatorBlock> {
        val criteriaList = mutableListOf<Criteria>()

        criteriaList.add(Criteria.where(ValidatorBlock::blockNumber.name).`is`(blockNumber))
        validator?.let {
            criteriaList.add(
                Criteria.where(ValidatorBlock::validator.name).`is`(it.value.lowercase())
            )
        }

        val query = Query(Criteria().andOperator(*criteriaList.toTypedArray()))

        return mongoTemplate.find<ValidatorBlock>(query)
    }

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

        val latestBeforeOrAt = { timestamp: Long ->
            validatorBlockRepository
                .findFirstByValidatorAndStatusAndBlockTimestampLessThanEqualOrderByBlockTimestampDesc(
                    validator,
                    BlockStatus.VALIDATED,
                    timestamp,
                )
        }

        return when (TimeSeriesUtils.selectResolution(endTimestamp - startTimestamp)) {
            TimeSeriesResolution.RAW ->
                validatorBlockRepository.findAllInTimestampRange(
                    startTimestamp,
                    endTimestamp,
                    validator,
                )
            TimeSeriesResolution.HOURLY ->
                TimeSeriesUtils.getBookendedRecords(
                    startTimestamp,
                    endTimestamp,
                    { start, end ->
                        validatorBlockRepository.findHourlyInTimestampRange(start, end, validator)
                    },
                    latestBeforeOrAt,
                )
            TimeSeriesResolution.DAILY ->
                TimeSeriesUtils.getBookendedRecords(
                    startTimestamp,
                    endTimestamp,
                    { start, end ->
                        validatorBlockRepository.findDailyInTimestampRange(start, end, validator)
                    },
                    latestBeforeOrAt,
                )
            TimeSeriesResolution.WEEKLY ->
                TimeSeriesUtils.getBookendedRecords(
                    startTimestamp,
                    endTimestamp,
                    { start, end ->
                        validatorBlockRepository.findWeeklyInTimestampRange(start, end, validator)
                    },
                    latestBeforeOrAt,
                )
            TimeSeriesResolution.MONTHLY ->
                TimeSeriesUtils.getBookendedRecords(
                    startTimestamp,
                    endTimestamp,
                    { start, end ->
                        validatorBlockRepository.findMonthlyInTimestampRange(start, end, validator)
                    },
                    latestBeforeOrAt,
                )
        }
    }

    open fun getValidators(
        validatorId: String?,
        endorser: String?,
        statuses: List<Status>?,
        pageable: Pageable,
    ): Slice<ValidatorResponse> {
        val criteriaList = mutableListOf<Criteria>()

        validatorId?.let { criteriaList.add(Criteria.where("_id").`is`(it.lowercase())) }
        endorser?.let {
            criteriaList.add(Criteria.where(Validator::endorser.name).`is`(it.lowercase()))
        }
        statuses?.let { criteriaList.add(Criteria.where(Validator::status.name).`in`(it)) }

        val query =
            if (criteriaList.isNotEmpty()) {
                Query(Criteria().andOperator(*criteriaList.toTypedArray()))
            } else {
                Query()
            }

        query.with(pageable).limit(pageable.pageSize + 1)

        val results = mongoTemplate.find<Validator>(query)
        val hasNext = results.size > pageable.pageSize
        val page = if (hasNext) results.dropLast(1) else results

        // Empty pages don't need price data — skip the oracle hop so a no-match query never
        // returns 503 just because the oracle happens to be flaky.
        if (page.isEmpty()) {
            return SliceImpl(emptyList(), pageable, hasNext)
        }

        val aggregates = aggregateService.build(page.map { it.id })
        val prices = priceFeedService.getPrices(setOf(PriceFeed.VET_USD, PriceFeed.VTHO_USD))
        val vetPrice = prices.getValue(PriceFeed.VET_USD)
        val vthoPrice = prices.getValue(PriceFeed.VTHO_USD)
        val mapped = page.map { ValidatorResponse.from(it, aggregates, vetPrice, vthoPrice) }

        return SliceImpl(mapped, pageable, hasNext)
    }

    open fun getValidatorById(validatorId: String): ValidatorResponse? {
        val query = Query(Criteria.where("_id").`is`(validatorId.lowercase()))
        val doc = mongoTemplate.findOne<Validator>(query) ?: return null
        val aggregates = aggregateService.build(listOf(doc.id))
        val prices = priceFeedService.getPrices(setOf(PriceFeed.VET_USD, PriceFeed.VTHO_USD))
        return ValidatorResponse.from(
            doc,
            aggregates,
            prices.getValue(PriceFeed.VET_USD),
            prices.getValue(PriceFeed.VTHO_USD),
        )
    }

    open fun getSlotStats(startTimestamp: Long, endTimestamp: Long): List<ValidatorSlotStats> {
        TimeValidationUtils.validateTimestamps(
            startTimestamp,
            endTimestamp,
            "startTimestamp",
            "endTimestamp",
        )
        return validatorBlockRepository.aggregateSlotStatsInTimestampRange(
            startTimestamp,
            endTimestamp,
        )
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
            .aggregateSlotStatsInTimestampRangeForValidator(startTimestamp, endTimestamp, validator)
            .firstOrNull()
    }

    open fun getCurrentBlockNumber(): Long =
        runBlocking { thorClient.getBlock(BlockRevision.Keyword.BEST) }.number
}
