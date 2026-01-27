package org.vechain.indexer.validator

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.math.BigDecimal
import java.sql.ResultSet
import org.bson.types.Decimal128
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.postgres.PostgresVersionedRepository
import org.vechain.indexer.stargate.token.TokenLevel

@Profile("validator", "validator-stats")
@Repository
open class PostgresValidatorRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<Validator>(jdbcTemplate, namedJdbcTemplate, objectMapper),
    ValidatorRepository {

    override fun tableName(): String = "validators"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        endorser, beneficiary, status, vet_staked, validator_vet_staked, delegator_vet_staked,
        queued_vet_staked, validator_queued_vet_staked, delegator_queued_vet_staked,
        validator_exiting_vet_staked, delegator_exiting_vet_staked, exiting_vet_staked,
        exiting_validator_vet_staked, cycle_end_block, total_rewards, block_probability,
        blocks_per_epoch, total_tvl, validator_tvl, delegator_tvl, validator_tvl_percentage,
        tvl_based_yield, validator_yield, avg_delegator_yield, next_cycle_tvl_based_yield,
        next_cycle_validator_yield, next_cycle_avg_delegator_yield, nft_yields_next_cycle,
        total_weight, online, completed_periods, start_block, cycle_period_length, blocks_per_year,
        percentage_offline, offline_blocks, exit_block, queue_position, available_start_block
        """
            .trimIndent()

    override fun insertPlaceholders(): String =
        "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"

    override fun insertParams(doc: Validator): Array<Any?> =
        arrayOf(
            doc.id,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.endorser,
            doc.beneficiary,
            doc.status?.name,
            doc.vetStaked?.bigDecimalValue(),
            doc.validatorVetStaked?.bigDecimalValue(),
            doc.delegatorVetStaked?.bigDecimalValue(),
            doc.queuedVetStaked?.bigDecimalValue(),
            doc.validatorQueuedVetStaked?.bigDecimalValue(),
            doc.delegatorQueuedVetStaked?.bigDecimalValue(),
            doc.validatorExitingVetStaked?.bigDecimalValue(),
            doc.delegatorExitingVetStaked?.bigDecimalValue(),
            doc.exitingVetStaked?.bigDecimalValue(),
            doc.exitingValidatorVetStaked,
            doc.cycleEndBlock,
            doc.totalRewards?.bigDecimalValue(),
            doc.blockProbability?.bigDecimalValue(),
            doc.blocksPerEpoch?.bigDecimalValue(),
            doc.totalTvl?.bigDecimalValue(),
            doc.validatorTvl?.bigDecimalValue(),
            doc.delegatorTvl?.bigDecimalValue(),
            doc.validatorTvlPercentage?.bigDecimalValue(),
            doc.tvlBasedYield?.bigDecimalValue(),
            doc.validatorYield?.bigDecimalValue(),
            doc.avgDelegatorYield?.bigDecimalValue(),
            doc.nextCycleTvlBasedYield?.bigDecimalValue(),
            doc.nextCycleValidatorYield?.bigDecimalValue(),
            doc.nextCycleAvgDelegatorYield?.bigDecimalValue(),
            doc.nftYieldsNextCycle?.let { map ->
                objectMapper.writeValueAsString(
                    map.mapKeys { it.key.name }.mapValues { it.value.bigDecimalValue().toString() }
                )
            },
            doc.totalWeight?.bigDecimalValue(),
            doc.online,
            doc.completedPeriods,
            doc.startBlock,
            doc.cyclePeriodLength,
            doc.blocksPerYear?.bigDecimalValue(),
            doc.percentageOffline?.bigDecimalValue(),
            doc.offlineBlocks,
            doc.exitBlock,
            doc.queuePosition,
            doc.availableStartBlock,
        )

    override fun mapRow(rs: ResultSet): Validator {
        val nftYieldsJson = rs.getString("nft_yields_next_cycle")
        val nftYieldsNextCycle: Map<TokenLevel, Decimal128>? =
            nftYieldsJson?.let {
                val rawMap: Map<String, String> = objectMapper.readValue(it)
                rawMap
                    .mapKeys { entry -> TokenLevel.valueOf(entry.key) }
                    .mapValues { entry -> Decimal128(BigDecimal(entry.value)) }
            }

        return Validator(
            id = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            endorser = rs.getString("endorser"),
            beneficiary = rs.getString("beneficiary"),
            status = rs.getString("status")?.let { Status.valueOf(it) },
            vetStaked = rs.getBigDecimal("vet_staked")?.let { Decimal128(it) },
            validatorVetStaked = rs.getBigDecimal("validator_vet_staked")?.let { Decimal128(it) },
            delegatorVetStaked = rs.getBigDecimal("delegator_vet_staked")?.let { Decimal128(it) },
            queuedVetStaked = rs.getBigDecimal("queued_vet_staked")?.let { Decimal128(it) },
            validatorQueuedVetStaked =
                rs.getBigDecimal("validator_queued_vet_staked")?.let { Decimal128(it) },
            delegatorQueuedVetStaked =
                rs.getBigDecimal("delegator_queued_vet_staked")?.let { Decimal128(it) },
            validatorExitingVetStaked =
                rs.getBigDecimal("validator_exiting_vet_staked")?.let { Decimal128(it) },
            delegatorExitingVetStaked =
                rs.getBigDecimal("delegator_exiting_vet_staked")?.let { Decimal128(it) },
            exitingVetStaked = rs.getBigDecimal("exiting_vet_staked")?.let { Decimal128(it) },
            exitingValidatorVetStaked =
                rs.getBigDecimal("exiting_validator_vet_staked") ?: BigDecimal.ZERO,
            cycleEndBlock = rs.getLongOrNull("cycle_end_block"),
            totalRewards = rs.getBigDecimal("total_rewards")?.let { Decimal128(it) },
            blockProbability = rs.getBigDecimal("block_probability")?.let { Decimal128(it) },
            blocksPerEpoch = rs.getBigDecimal("blocks_per_epoch")?.let { Decimal128(it) },
            totalTvl = rs.getBigDecimal("total_tvl")?.let { Decimal128(it) },
            validatorTvl = rs.getBigDecimal("validator_tvl")?.let { Decimal128(it) },
            delegatorTvl = rs.getBigDecimal("delegator_tvl")?.let { Decimal128(it) },
            validatorTvlPercentage =
                rs.getBigDecimal("validator_tvl_percentage")?.let { Decimal128(it) },
            tvlBasedYield = rs.getBigDecimal("tvl_based_yield")?.let { Decimal128(it) },
            validatorYield = rs.getBigDecimal("validator_yield")?.let { Decimal128(it) },
            avgDelegatorYield = rs.getBigDecimal("avg_delegator_yield")?.let { Decimal128(it) },
            nextCycleTvlBasedYield =
                rs.getBigDecimal("next_cycle_tvl_based_yield")?.let { Decimal128(it) },
            nextCycleValidatorYield =
                rs.getBigDecimal("next_cycle_validator_yield")?.let { Decimal128(it) },
            nextCycleAvgDelegatorYield =
                rs.getBigDecimal("next_cycle_avg_delegator_yield")?.let { Decimal128(it) },
            nftYieldsNextCycle = nftYieldsNextCycle,
            totalWeight = rs.getBigDecimal("total_weight")?.let { Decimal128(it) },
            online = rs.getObject("online") as? Boolean,
            completedPeriods = rs.getLongOrNull("completed_periods"),
            startBlock = rs.getLongOrNull("start_block"),
            cyclePeriodLength = rs.getLongOrNull("cycle_period_length"),
            blocksPerYear = rs.getBigDecimal("blocks_per_year")?.let { Decimal128(it) },
            percentageOffline = rs.getBigDecimal("percentage_offline")?.let { Decimal128(it) },
            offlineBlocks = rs.getLongOrNull("offline_blocks"),
            exitBlock = rs.getLongOrNull("exit_block"),
            queuePosition = rs.getLongOrNull("queue_position"),
            availableStartBlock = rs.getLongOrNull("available_start_block"),
        )
    }

    override fun saveAllVersioned(updated: List<Validator>, existing: List<Validator>) {
        super.saveAllVersioned(updated, existing)
    }

    override fun saveAll(validators: List<Validator>) {
        if (validators.isEmpty()) return

        // For saveAll without existing, we need to handle updates properly
        val entityIds = validators.map { it.id }

        // Mark existing current versions as non-current
        if (entityIds.isNotEmpty()) {
            namedJdbcTemplate.update(
                """
                UPDATE ${tableName()}
                SET is_current = false
                WHERE ${entityIdColumn()} IN (:entityIds) AND is_current = true
                """
                    .trimIndent(),
                mapOf("entityIds" to entityIds),
            )
        }

        // Insert new versions
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO ${tableName()} (${insertColumns()})
            VALUES (${insertPlaceholders()})
            ON CONFLICT (${entityIdColumn()}, version) DO UPDATE SET is_current = true
            """
                .trimIndent(),
            validators.map { insertParams(it) },
        )
    }

    override fun findAllById(ids: Collection<String>): List<Validator> {
        if (ids.isEmpty()) return emptyList()

        return namedJdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE ${entityIdColumn()} IN (:ids) AND is_current = true
            """
                .trimIndent(),
            mapOf("ids" to ids),
        ) { rs, _ ->
            mapRow(rs)
        }
    }

    override fun findById(id: String): Validator? {
        return findCurrentByEntityId(id)
    }

    override fun findByEndorser(endorser: String, pageable: Pageable): Slice<Validator> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE endorser = ? AND is_current = true
                ORDER BY entity_id ASC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                endorser,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findByStatusNot(status: Status): List<Validator> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE status != ? AND is_current = true
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            status.name,
        )
    }

    override fun findByStatus(status: Status): List<Validator> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE status = ? AND is_current = true
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            status.name,
        )
    }
}
