package org.vechain.indexer.validators

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.timeseries.TimeSeriesRecord
import org.vechain.indexer.utils.TimeSeriesUtils
import org.vechain.indexer.validator.RewardValues
import org.vechain.indexer.validator.ValidatorReward
import org.vechain.indexer.validator.ValidatorRewardRepository

@Profile("validator")
@Service
open class ValidatorService(private val validatorRewardRepository: ValidatorRewardRepository) {
    /**
     * Service method to get validator reward records with flexible filters.
     *
     * Cases:
     * - Validator + BlockNumber → latest reward for that validator before or at blockNumber
     * - Validator only → most recent reward for that validator
     * - BlockNumber only → latest reward across all validators before or at blockNumber
     * - Neither → absolute latest reward record (across all validators)
     */
    open fun getValidatorRewards(validator: Address?, blockNumber: Long?): ValidatorReward? =
        if (blockNumber != null && validator != null) {
            validatorRewardRepository.findLatestByValidatorBeforeOrAtBlockNumber(
                HexUtils.normalise(validator.toString()),
                blockNumber,
            )
        } else if (validator != null) {
            validatorRewardRepository.findLatestByValidator(
                HexUtils.normalise(validator.toString())
            )
        } else if (blockNumber != null) {
            validatorRewardRepository.findLatestBeforeOrAtBlockNumber(blockNumber)
        } else {
            validatorRewardRepository.getLatestRecord()
        }

    open fun getValidatorRewardsHistoric(
        after: Long,
        before: Long,
        validator: Address? = null,
    ): List<TimeSeriesRecord<RewardValues>> =
        if (validator != null) {
            val normalizeValidator = HexUtils.normalise(validator.toString())
            TimeSeriesUtils.getHistoricTimeSeries(
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
        } else {
            TimeSeriesUtils.getHistoricTimeSeries(
                after,
                before,
                { a, b -> validatorRewardRepository.findByBlockTimestampBetween(a, b) },
                { ts -> validatorRewardRepository.findLatestBeforeOrAtBlockTimestamp(ts) },
            ) {
                RewardValues(
                    blockReward = it.blockReward,
                    priorityReward = it.priorityReward,
                    total = it.total,
                )
            }
        }
}
