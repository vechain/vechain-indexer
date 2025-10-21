package org.vechain.indexer.validators

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.vechain.indexer.client.ThorClient
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
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
import org.vechain.indexer.validators.ErrorMessages.ERROR_INVALID_START_AND_END_BLOCK_NUMBERS
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

        validator?.let { criteriaList.add(Criteria.where("validator").`is`(it.value.lowercase())) }
        status?.let { criteriaList.add(Criteria.where("status").`is`(it)) }
        blockNumber?.let { criteriaList.add(Criteria.where("blockNumber").`is`(it)) }

        val query =
            if (criteriaList.isNotEmpty()) {
                Query(Criteria().andOperator(*criteriaList.toTypedArray()))
            } else {
                Query()
            }

        query
            .with(pageable)
            .with(
                org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Direction.DESC,
                    "blockNumber",
                )
            )

        val results = mongoTemplate.find(query, ValidatorBlock::class.java)
        val slice = SliceImpl(results, pageable, results.size == pageable.pageSize)

        return paginatedResponse(slice)
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
        status: Status?,
        pageable: Pageable,
        sortField: String,
        direction: String?,
    ): Slice<Validator> {
        val criteriaList = mutableListOf<Criteria>()

        validatorId?.let { criteriaList.add(Criteria.where("_id").`is`(it.lowercase())) }
        endorser?.let { criteriaList.add(Criteria.where("endorser").`is`(it.lowercase())) }
        status?.let { criteriaList.add(Criteria.where("status").`is`(it)) }

        val query =
            if (criteriaList.isNotEmpty()) {
                Query(Criteria().andOperator(*criteriaList.toTypedArray()))
            } else {
                Query()
            }

        // build Sort from the resolved field + direction
        val dir = Sort.Direction.fromString(direction ?: "desc")
        val sort = Sort.by(dir, sortField)

        query.with(pageable).with(sort)

        val results = mongoTemplate.find(query, Validator::class.java)
        val slice = SliceImpl(results, pageable, results.size == pageable.pageSize)

        return slice
    }

    open fun getMissedBlocksPercentage(
        validator: String,
        startBlock: Long,
        endBlock: Long?,
    ): Double {
        val actualEnd = endBlock ?: thorClient.getBestBlock().number

        if (actualEnd <= startBlock) {
            throw BadRequestException(ERROR_INVALID_START_AND_END_BLOCK_NUMBERS)
        }

        val criteria = mutableListOf<Criteria>()
        criteria.add(Criteria.where("validator").`is`(validator))
        criteria.add(Criteria.where("status").`is`(BlockStatus.MISSED))
        criteria.add(Criteria.where("blockNumber").gte(startBlock))
        criteria.add(Criteria.where("blockNumber").lte(actualEnd))

        val query = Query(Criteria().andOperator(*criteria.toTypedArray()))
        val missedCount = mongoTemplate.count(query, ValidatorBlock::class.java)

        val totalBlocks = (actualEnd - startBlock + 1).toDouble()
        return if (totalBlocks > 0) (missedCount.toDouble() / totalBlocks) * 100.0 else 0.0
    }
}
