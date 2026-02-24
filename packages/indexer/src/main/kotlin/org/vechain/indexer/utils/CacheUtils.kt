package org.vechain.indexer.utils

import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

object CacheUtils {
    /**
     * Update a cache reference only after the current transaction commits. If no transaction
     * synchronization is active (e.g. in unit tests), falls back to updating the cache directly.
     *
     * @param value The value to cache after commit.
     * @param setter Callback to set the cache field.
     * @param clear Callback to clear the cache field on rollback.
     */
    fun <T> updateAfterCommit(value: T, setter: (T) -> Unit, clear: () -> Unit) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        setter(value)
                    }

                    override fun afterCompletion(status: Int) {
                        if (status != TransactionSynchronization.STATUS_COMMITTED) {
                            clear()
                        }
                    }
                }
            )
        } else {
            setter(value)
        }
    }
}
