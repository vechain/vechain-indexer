package org.vechain.indexer.backfill

import org.springframework.stereotype.Component
import org.vechain.indexer.chain.ChainHead
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresProperties
import org.vechain.indexer.postgres.IndexBuilder
import org.vechain.indexer.postgres.IndexSet

/** One coordinator per schema, built where the processor that owns the schema is built. */
@Component
@ConditionalOnPostgres
class BackfillCoordinatorFactory(
    private val postgres: PostgresProperties,
    private val properties: BackfillProperties,
    private val chainHead: ChainHead,
    private val state: BackfillState,
) {

    fun create(indexSet: IndexSet): BackfillCoordinator =
        BackfillCoordinator(
            indexSet,
            IndexBuilder(
                postgres,
                IndexBuilder.Settings(
                    workers = properties.buildWorkers,
                    maintenanceWorkMem = properties.maintenanceWorkMem,
                    parallelMaintenanceWorkers = properties.parallelMaintenanceWorkers,
                ),
            ),
            chainHead,
            properties,
            state,
        )
}
