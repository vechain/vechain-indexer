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
import org.vechain.indexer.validator.ValidatorReward
import org.vechain.indexer.validator.ValidatorRewardRepository

@Profile("validator")
@Service
open class ValidatorService(private val validatorRewardRepository: ValidatorRewardRepository) {
    open fun getValidatorRewards(
        validator: Address?,
        blockNumber: Long?,
        pageable: Pageable,
    ): PaginatedResponse<ValidatorReward> {
        val result =
            if (blockNumber != null && validator != null) {
                validatorRewardRepository.findLatestByValidatorBeforeOrAtBlockNumber(
                    validator.value.lowercase(),
                    blockNumber,
                    pageable,
                )
            } else if (validator != null) {
                validatorRewardRepository.findByValidator(validator.value.lowercase(), pageable)
            } else if (blockNumber != null) {
                validatorRewardRepository.findByBlockNumber(blockNumber, pageable)
            } else {
                validatorRewardRepository.findAllByOrderByBlockNumberDesc(pageable)
            }

        return paginatedResponse(result)
    }

    open fun getValidatorRewardsHistoric(
        after: Long,
        before: Long,
        validator: Address,
    ): List<TimeSeriesRecord<RewardValues>> {
        val normalizeValidator = validator.value.lowercase()
        return TimeSeriesUtils.getHistoricTimeSeries(
            after,
            before,
            { a, b ->
                validatorRewardRepository.findByValidatorAndBlockTimestampBetween(
                    normalizeValidator,
                    a,
                    b,
                )
            },
            { ts ->
                validatorRewardRepository.findLatestByValidatorBeforeOrAtBlockTimestamp(
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
