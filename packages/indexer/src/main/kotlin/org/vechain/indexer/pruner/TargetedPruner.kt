package org.vechain.indexer.pruner

import org.vechain.indexer.Pruner
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive

interface TargetedPruner<T : VersionedDocument, S : Archive<T>> : Pruner {
    fun run(currentBlockNumber: Long, idsToPrune: List<String>?)
}
