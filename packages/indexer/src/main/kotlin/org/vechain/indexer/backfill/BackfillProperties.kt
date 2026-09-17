package org.vechain.indexer.backfill

import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "indexer.backfill")
class BackfillProperties {
    /** Off leaves every index in place, whatever the gap. */
    var enabled: Boolean = true

    /** The gap at which the write saving overtakes the one rebuild: a resync, not a restore. */
    var enterBehindBlocks: Long = 500_000

    /** Indexes grown at once by a paused rebuild, each on its own connection. */
    var buildWorkers: Int = 4

    /** `maintenance_work_mem` per build session; [buildWorkers] of them exist at once. */
    var maintenanceWorkMem: String = "1GB"

    /** `max_parallel_maintenance_workers` per build session, for the sort. */
    var parallelMaintenanceWorkers: Int = 2

    @PostConstruct
    fun validate() {
        require(enterBehindBlocks > 0) {
            "indexer.backfill.enter-behind-blocks must be > 0, was $enterBehindBlocks"
        }
        require(buildWorkers >= 1) {
            "indexer.backfill.build-workers must be >= 1, was $buildWorkers"
        }
    }
}
