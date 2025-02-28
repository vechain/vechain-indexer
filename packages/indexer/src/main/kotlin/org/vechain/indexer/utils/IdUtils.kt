package org.vechain.indexer.utils

import org.apache.commons.codec.digest.DigestUtils
import org.vechain.indexer.event.model.generic.GenericEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.VersionedDocument
import org.vechain.indexer.utils.ParamUtils.getAsString

object IdUtils {
    fun buildArchiveId(
        document: VersionedDocument,
        version: Int = document.version,
    ): String = buildHashedId("${document::class.simpleName}-${document.getDocumentId()}-$version")

    fun buildNftId(transferEvent: Pair<IndexedEvent, GenericEventParameters>): String =
        buildHashedId(
            "${transferEvent.first.address}-${transferEvent.second.params.getAsString("tokenId")}",
        )
}

private fun buildHashedId(plainId: String): String = DigestUtils.sha1Hex(plainId)
