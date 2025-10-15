package org.vechain.indexer.validators

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.SliceImpl
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.timeseries.TimeSeriesRecord
import org.vechain.indexer.utils.TimeSeriesUtils
import org.vechain.indexer.validator.BlockStatus
import org.vechain.indexer.validator.RewardValues
import org.vechain.indexer.validator.ValidatorBlock
import org.vechain.indexer.validator.ValidatorBlockRepository

@Profile("validator")
@Service
open class ValidatorService(
    private val validatorBlockRepository: ValidatorBlockRepository,
    private val mongoTemplate: MongoTemplate,
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
                validatorBlockRepository.findByValidatorAndStatusAndBlockTimestampBetween(
                    normalizeValidator,
                    BlockStatus.VALIDATED,
                    a,
                    b,
                )
            },
            { ts ->
                validatorBlockRepository.findLatestByValidatorAndStatusBeforeOrAtBlockTimestamp(
                    normalizeValidator,
                    BlockStatus.VALIDATED,
                    ts,
                )
            },
        ) {
            RewardValues(
                blockReward = it.blockReward!!,
                priorityReward = it.priorityReward!!,
                total = it.total!!,
            )
        }
    }
}
