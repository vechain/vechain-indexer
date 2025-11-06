package org.vechain.indexer.utils

import org.apache.commons.codec.digest.DigestUtils
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsString

object IdUtils {
    fun buildArchiveId(document: VersionedDocument, version: Int = document.version): String =
        buildHashedId("${document::class.simpleName}-${document.getDocumentId()}-$version")

    fun buildNftId(transferEvent: IndexedEvent): String =
        buildHashedId("${transferEvent.address}-${transferEvent.params.getAsString("tokenId")}")

    fun buildHashedId(plainId: String): String = DigestUtils.sha1Hex(plainId)
}
