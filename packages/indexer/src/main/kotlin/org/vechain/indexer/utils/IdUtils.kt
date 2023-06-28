package org.vechain.indexer.utils

import org.apache.commons.codec.digest.DigestUtils
import org.vechain.indexer.model.VersionedDocument

object IdUtils {
    fun buildHashedId(plainId: String) = DigestUtils.sha1Hex(plainId)

    fun buildArchiveId(document: VersionedDocument, version: Int = document.version): String =
        buildHashedId("${document::class.simpleName}-${document.getDocumentId()}-${version}")
}
