package org.vechain.indexer.config

import java.util.concurrent.atomic.AtomicReference
import org.springframework.stereotype.Component

@Component
class IndexBootstrapState {
    enum class Status {
        NOT_STARTED,
        RUNNING,
        READY,
        FAILED,
    }

    data class Snapshot(val status: Status, val message: String)

    private val snapshot =
        AtomicReference(
            Snapshot(
                status = Status.NOT_STARTED,
                message = "Collection and index bootstrap has not started yet.",
            )
        )

    fun markRunning(initializerCount: Int) {
        snapshot.set(
            Snapshot(
                status = Status.RUNNING,
                message = "Initializing $initializerCount collection bootstrap tasks.",
            )
        )
    }

    fun markReady(initializerCount: Int) {
        snapshot.set(
            Snapshot(
                status = Status.READY,
                message = "Completed $initializerCount collection bootstrap tasks.",
            )
        )
    }

    fun markFailed(throwable: Throwable) {
        snapshot.set(
            Snapshot(
                status = Status.FAILED,
                message = throwable.message ?: "Collection and index bootstrap failed.",
            )
        )
    }

    fun snapshot(): Snapshot = snapshot.get()
}
