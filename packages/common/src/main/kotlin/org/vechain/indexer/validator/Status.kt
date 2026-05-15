package org.vechain.indexer.validator

enum class Status {
    NONE,
    QUEUED,
    ACTIVE,
    EXITING, // exit signaled, waiting for ExitBlock
    EXITED, // past ExitBlock; cooldown or withdrawable derived from blockNumber
    WITHDRAWN // ValidationWithdrawn observed; terminal
    ;

    companion object {
        fun fromCode(code: Int): Status =
            when (code) {
                0 -> NONE
                1 -> QUEUED
                2 -> ACTIVE
                3 -> EXITED
                4 -> EXITING
                else -> NONE
            }
    }
}
