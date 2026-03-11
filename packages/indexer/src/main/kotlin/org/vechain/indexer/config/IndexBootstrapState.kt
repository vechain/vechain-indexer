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

    private val status = AtomicReference(Status.NOT_STARTED)
    private val message = AtomicReference("Collection and index bootstrap has not started yet.")

    fun markRunning(initializerCount: Int) {
        status.set(Status.RUNNING)
        message.set("Initializing $initializerCount collection bootstrap tasks.")
    }

    fun markReady(initializerCount: Int) {
        status.set(Status.READY)
        message.set("Completed $initializerCount collection bootstrap tasks.")
    }

    fun markFailed(throwable: Throwable) {
        status.set(Status.FAILED)
        message.set(throwable.message ?: "Collection and index bootstrap failed.")
    }

    fun snapshot(): Snapshot = Snapshot(status = status.get(), message = message.get())
}
