package org.vechain.indexer.validator

enum class Status {
    UNKNOWN, // Initial state, should not be used
    QUEUED, // Waiting to be activated
    ACTIVE, // Fully active and validating
    EXITED, // Exited and funds withdrawn
    EXITING, // Pending exit (exit signaled, waiting in exit queue)
    LEAVING_QUE // Signalled to exit while in que
    ;

    companion object {
        fun fromCode(code: Int): Status =
            when (code) {
                0 -> UNKNOWN
                1 -> QUEUED
                2 -> ACTIVE
                3 -> EXITED
                4 -> EXITING
                5 -> LEAVING_QUE
                else -> UNKNOWN
            }
    }
}
