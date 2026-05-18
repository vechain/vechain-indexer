package org.vechain.indexer

// Extends IllegalStateException so indexer-core's alignComponents catches and aggregates these
// with other unhappy indexers in the same dependency component, giving the operator a single
// "drop persisted state for these indexers" list instead of a runner crash.
class RollbackException(message: String) : IllegalStateException(message)
