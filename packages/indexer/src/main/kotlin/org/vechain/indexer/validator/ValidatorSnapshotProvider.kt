package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

/**
 * Mongo-backed validator cycle snapshot provider for downstream indexers.
 *
 * [ValidatorService] refreshes chain-derived cycle fields during the staker walk: on cold start and
 * every validator epoch boundary. Downstream indexers can therefore reuse this cache instead of
 * re-reading the whole validator collection every block.
 */
@Profile(
    "validator",
    "delegation",
    "stargate",
    "stargate-token",
    "history",
    "validator-reward",
    "token-reward",
    "vet-delegated-by-block",
)
@Service
open class ValidatorSnapshotProvider(private val repository: ValidatorRepository) {
    private var loaded = false
    private var snapshotsById: Map<String, ValidatorSnapshot> = emptyMap()

    open fun snapshotsForBlock(blockNumber: Long): ValidatorSnapshotSet {
        val shouldRefresh = !loaded || isEpochBoundary(blockNumber)
        if (shouldRefresh) {
            snapshotsById =
                repository
                    .findByStatusNot(Status.WITHDRAWN)
                    .mapNotNull { it.toSnapshot() }
                    .associateBy { it.validatorId }
            loaded = true
        }
        return ValidatorSnapshotSet(snapshotsById, refreshed = shouldRefresh)
    }

    open fun snapshotForValidator(
        validatorId: String,
        blockNumber: Long,
        preferredSnapshots: Map<String, ValidatorSnapshot> = emptyMap(),
    ): ValidatorSnapshot? {
        val normalized = validatorId.lowercase()
        preferredSnapshots[normalized]?.let {
            return it
        }
        snapshotsById[normalized]?.let {
            return it
        }

        val snapshot = repository.findByIdOrNull(normalized)?.toSnapshot() ?: return null
        snapshotsById = snapshotsById + (normalized to snapshot)
        if (!loaded && !isEpochBoundary(blockNumber)) {
            loaded = true
        }
        return snapshot
    }

    open fun invalidateCache() {
        loaded = false
        snapshotsById = emptyMap()
    }

    private fun Validator.toSnapshot(): ValidatorSnapshot? {
        val period = cyclePeriodLength ?: return null
        return ValidatorSnapshot(
            validatorId = id.lowercase(),
            stakingPeriodLength = period,
            startBlock = startBlock ?: 0L,
            exitBlock = exitBlock ?: 0L,
        )
    }

    private fun isEpochBoundary(blockNumber: Long): Boolean = blockNumber % EPOCH_LENGTH == 0L

    companion object {
        const val EPOCH_LENGTH = 180L
    }
}

data class ValidatorSnapshotSet(
    val snapshots: Map<String, ValidatorSnapshot>,
    val refreshed: Boolean,
)
