package org.vechain.indexer.validator

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.pruner.PostgresPruner
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.validator.domain.ValidatorDecoder.buildClauses
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("validator", "validator-stats")
open class ValidatorConfig(private val indexerVersionService: IndexerVersionService) {
    @Value("\${indexer.version.validator:1}") private var version: Int = 1

    @PostConstruct
    open fun initVersionCheck() {
        indexerVersionService.ensureTableExists(
            indexerName = IndexerNames.VALIDATOR,
            tableName = "validators",
            schemaResource = "db/tables/validators.sql",
            newVersion = version,
        )
    }

    @Bean
    open fun validatorPruner(
        jdbcTemplate: JdbcTemplate,
        namedJdbcTemplate: NamedParameterJdbcTemplate,
        @Value("\${indexer.pruner.prune-block-depth:10000}") pruneBlockDepth: Long,
    ): PostgresPruner =
        PostgresPruner(jdbcTemplate, namedJdbcTemplate, pruneBlockDepth, "validators")

    @Bean
    open fun validatorIndexer(
        thorClient: ThorClient,
        processor: ValidatorProcessor,
        validatorPruner: PostgresPruner,
        @Value("\${indexer.start-block.validator}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLogInterval: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}")
        builtinStakerAddress: String,
        @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
        getAllValidatorsAddress: String,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.VALIDATOR)
            .thorClient(thorClient)
            .processor(processor)
            .pruner(validatorPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .blockBatchSize(syncBlockBatchSize)
            .includeFullBlock()
            .abis("abis/stargate")
            .abiContracts(listOf(builtinStakerAddress))
            .abiEventNames(listOf("BeneficiarySet", "StakeDecreased"))
            .callDataClauses(buildClauses(getAllValidatorsAddress))
            .excludeVetTransfers()
            .build()
}
