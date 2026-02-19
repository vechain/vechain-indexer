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
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.explorer.TimestampUtils.SECONDS_PER_DAY
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.utils.TimeSeriesUtils.DAILY_THRESHOLD
import org.vechain.indexer.utils.TimeSeriesUtils.HOURLY_THRESHOLD
import org.vechain.indexer.utils.TimeSeriesUtils.MONTHLY_THRESHOLD
import org.vechain.indexer.utils.TimeSeriesUtils.WEEKLY_THRESHOLD
import org.vechain.indexer.validator.BlockStatus
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorBlock
import org.vechain.indexer.validator.ValidatorBlockRepository
import org.vechain.indexer.validators.ErrorMessages.ERROR_END_TIME_CANNOT_BE_LESS_THAN_START_TIME
import org.vechain.indexer.validators.ErrorMessages.ERROR_START_TIME_CANNOT_BE_NEGATIVE

@Profile("validator")
@Service
open class ValidatorService(
    private val validatorBlockRepository: ValidatorBlockRepository,
    private val mongoTemplate: MongoTemplate,
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
        if (startTimestamp < 0) {
            throw BadRequestException(ERROR_START_TIME_CANNOT_BE_NEGATIVE)
        }
        if (endTimestamp <= startTimestamp) {
            throw BadRequestException(ERROR_END_TIME_CANNOT_BE_LESS_THAN_START_TIME)
        }

        val timeRange = endTimestamp - startTimestamp

        return when {
            timeRange <= HOURLY_THRESHOLD -> {
                // Return all blocks for small ranges
                validatorBlockRepository.findAllInTimestampRange(
                    startTimestamp,
                    endTimestamp,
                    validator,
                )
            }
            timeRange <= DAILY_THRESHOLD -> {
                // Return hourly aggregates
                validatorBlockRepository.findHourlyInTimestampRange(
                    startTimestamp,
                    endTimestamp,
                    validator,
                )
            }
            timeRange <= WEEKLY_THRESHOLD -> {
                // Return daily aggregates
                validatorBlockRepository.findDailyInTimestampRange(
                    startTimestamp,
                    endTimestamp,
                    validator,
                )
            }
            timeRange <= MONTHLY_THRESHOLD -> {
                // Return weekly aggregates
                validatorBlockRepository.findWeeklyInTimestampRange(
                    startTimestamp,
                    endTimestamp,
                    validator,
                )
            }
            else -> {
                // Return monthly aggregates
                validatorBlockRepository.findMonthlyInTimestampRange(
                    startTimestamp,
                    endTimestamp,
                    validator,
                )
            }
        }
    }

    open fun getValidators(
        validatorId: String?,
        endorser: String?,
        statuses: List<Status>?,
        pageable: Pageable,
    ): Slice<Validator> {
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
        return SliceImpl(page, pageable, hasNext)
    }

    open fun getValidatorById(validatorId: String): Validator? {
        val query = Query(Criteria.where("_id").`is`(validatorId.lowercase()))
        return mongoTemplate.findOne<Validator>(query)
    }

    open fun getMissedBlocksPercentage(
        timeframe: MissedBlocksTimeframe,
        validator: String? = null,
    ): AllValidatorsMissedBlocksResponse {
        val currentBlock = runBlocking { thorClient.getBlock(BlockRevision.Keyword.BEST) }.number
        val blocksPerSecond = 10L // VeChain produces ~1 block per 10 seconds

        val startBlock =
            when (timeframe) {
                MissedBlocksTimeframe.DAY -> currentBlock - (SECONDS_PER_DAY / blocksPerSecond)
                MissedBlocksTimeframe.WEEK ->
                    currentBlock - (7L * SECONDS_PER_DAY / blocksPerSecond)
                MissedBlocksTimeframe.MONTH ->
                    currentBlock - (30L * SECONDS_PER_DAY / blocksPerSecond)
                MissedBlocksTimeframe.YEAR ->
                    currentBlock - (365L * SECONDS_PER_DAY / blocksPerSecond)
            }.coerceAtLeast(0L)

        val missedDocs =
            if (validator != null) {
                validatorBlockRepository.findMissedInRange(validator, startBlock, currentBlock)
            } else {
                validatorBlockRepository.findAllMissedInRange(startBlock, currentBlock)
            }

        // Group by validator and calculate missed blocks for each
        val validatorMissedMap = mutableMapOf<String, Long>()

        for (doc in missedDocs) {
            val validatorAddr = doc.validator
            val offlineStart = if (doc.blockNumber < startBlock) startBlock else doc.blockNumber
            val offlineEnd = doc.onlineBlock ?: currentBlock

            if (offlineEnd >= offlineStart) {
                val missedCount = offlineEnd - offlineStart + 1L
                validatorMissedMap[validatorAddr] =
                    (validatorMissedMap[validatorAddr] ?: 0L) + missedCount
            }
        }

        val totalBlocks = (currentBlock - startBlock + 1L).toDouble()

        val validatorStats =
            validatorMissedMap
                .map { (validatorAddr, missedBlocks) ->
                    ValidatorMissedBlocksPercentage(
                        validator = validatorAddr,
                        missedPercentage =
                            if (totalBlocks > 0) (missedBlocks.toDouble() / totalBlocks) * 100.0
                            else 0.0,
                    )
                }
                .sortedByDescending { it.missedPercentage }

        return AllValidatorsMissedBlocksResponse(
            timeframe = timeframe,
            startBlock = startBlock,
            endBlock = currentBlock,
            validators = validatorStats,
        )
    }
}
