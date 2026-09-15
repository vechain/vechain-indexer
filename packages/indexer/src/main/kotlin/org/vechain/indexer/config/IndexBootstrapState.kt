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
                message = "Startup preload has not started yet.",
            )
        )

    fun markRunning() {
        snapshot.set(Snapshot(status = Status.RUNNING, message = "Startup preload is running."))
    }

    fun markReady() {
        snapshot.set(Snapshot(status = Status.READY, message = "Startup preload completed."))
    }

    fun markFailed(throwable: Throwable) {
        snapshot.set(
            Snapshot(
                status = Status.FAILED,
                message = throwable.message ?: "Startup preload failed.",
            )
        )
    }

    fun snapshot(): Snapshot = snapshot.get()
}
