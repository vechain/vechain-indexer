package org.vechain.indexer.validators

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.timeseries.TimeSeriesRecord
import org.vechain.indexer.utils.TimeSeriesUtils
import org.vechain.indexer.validator.RewardValues
import org.vechain.indexer.validator.ValidatorBlock
import org.vechain.indexer.validator.ValidatorBlockRepository

@Profile("validator")
@Service
open class ValidatorService(private val validatorBlockRepository: ValidatorBlockRepository) {
    open fun getValidatorBlocks(
        validator: Address?,
        blockNumber: Long?,
        pageable: Pageable,
    ): PaginatedResponse<ValidatorBlock> {
        val result =
            if (blockNumber != null && validator != null) {
                validatorBlockRepository.findLatestByValidatorBeforeOrAtBlockNumber(
                    validator.value.lowercase(),
                    blockNumber,
                    pageable,
                )
            } else if (validator != null) {
                validatorBlockRepository.findByValidator(validator.value.lowercase(), pageable)
            } else if (blockNumber != null) {
                validatorBlockRepository.findByBlockNumber(blockNumber, pageable)
            } else {
                validatorBlockRepository.findAllByOrderByBlockNumberDesc(pageable)
            }

        return paginatedResponse(result)
    }

    open fun getValidatorBlocksHistoric(
        after: Long,
        before: Long,
        validator: Address,
    ): List<TimeSeriesRecord<RewardValues>> {
        val normalizeValidator = validator.value.lowercase()
        return TimeSeriesUtils.getHistoricTimeSeries(
            after,
            before,
            { a, b ->
                validatorBlockRepository.findByValidatorAndBlockTimestampBetween(
                    normalizeValidator,
                    a,
                    b,
                )
            },
            { ts ->
                validatorBlockRepository.findLatestByValidatorBeforeOrAtBlockTimestamp(
                    normalizeValidator,
                    ts,
                )
            },
        ) {
            RewardValues(
                blockReward = it.blockReward,
                priorityReward = it.priorityReward,
                total = it.total,
            )
        }
    }
}
