package org.vechain.indexer.pruner

import org.vechain.indexer.Pruner
import org.vechain.indexer.VersionedDocument

interface TargetedPruner<T : VersionedDocument> : Pruner {
    fun run(currentBlockNumber: Long, idsToPrune: List<String>?)
}
