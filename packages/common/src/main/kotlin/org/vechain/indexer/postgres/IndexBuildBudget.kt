package org.vechain.indexer.postgres

import java.util.concurrent.Semaphore

/** The whole task's index-build concurrency: group members rebuild together, not in turn. */
class IndexBuildBudget(val permits: Int) {

    private val semaphore = Semaphore(permits)

    fun <T> withPermit(build: () -> T): T {
        semaphore.acquire()
        try {
            return build()
        } finally {
            semaphore.release()
        }
    }
}
