package org.vechain.indexer

import org.slf4j.LoggerFactory

data class ResolvedRecord<T>(val existing: T?, val nextVersion: Int)

class VersionedDocumentAccumulator<T : VersionedDocument>(
    private val findById: (String) -> T?,
    private val initialVersion: Int = 1,
) {
    private val logger = LoggerFactory.getLogger(VersionedDocumentAccumulator::class.java)

    private val updated = mutableMapOf<String, T>()
    private val archived = mutableMapOf<String, T>()
    private val archivedInCurrentBlock = mutableSetOf<String>()

    /**
     * Signals the start of a new block. Resets per-block tracking so that the next update to each
     * recordId will archive the pre-block state.
     */
    fun startBlock() {
        archivedInCurrentBlock.clear()
    }

    /**
     * Resolves the existing record and computes the correct next version:
     * - New record (no existing): [initialVersion]
     * - First update in current block: existing.version + 1
     * - Subsequent update in same block: existing.version (no increment)
     */
    fun resolve(recordId: String): ResolvedRecord<T> {
        val fromCache = updated[recordId]
        val fromDb =
            if (fromCache == null) {
                val result = findById(recordId)
                // Guard against Spring Data returning the wrong document (observed in
                // production with CheckpointFilteringMongoTemplate — findByIdOrNull can
                // return a document whose _id doesn't match the requested ID).
                if (result != null && result.getDocumentId() != recordId) {
                    error(
                        "findById returned wrong document: requested=$recordId, got=${result.getDocumentId()} (v${result.version})"
                    )
                } else {
                    result
                }
            } else {
                null
            }
        val existing = fromCache ?: fromDb
        val nextVersion =
            when {
                existing == null -> initialVersion
                recordId in archivedInCurrentBlock -> existing.version
                else -> existing.version + 1
            }
        return ResolvedRecord(existing, nextVersion)
    }

    /**
     * Stores [updatedRecord] as the latest version for [recordId]. If [existing] is non-null and
     * this is the first update for [recordId] in the current block, the existing record is
     * archived.
     */
    fun put(recordId: String, existing: T?, updatedRecord: T) {
        if (existing != null && recordId !in archivedInCurrentBlock) {
            archived["${existing.getDocumentId()}_${existing.version}"] = existing
            archivedInCurrentBlock.add(recordId)
        }
        updated[recordId] = updatedRecord
    }

    /** Returns (updated records, archived records). */
    fun results(): Pair<List<T>, List<T>> {
        val updatedList = updated.values.toList()
        val archivedList = archived.values.toList()

        logger.debug(
            "Accumulator results: {} updated records, {} archived records",
            updatedList.size,
            archivedList.size,
        )

        return updatedList to archivedList
    }
}
