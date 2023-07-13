package org.vechain.indexer.utils

import org.apache.commons.codec.digest.DigestUtils
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.model.VersionedDocument
import org.web3j.utils.Numeric

object IdUtils {

    fun buildArchiveId(document: VersionedDocument, version: Int = document.version): String =
        buildHashedId("${document::class.simpleName}-${document.getDocumentId()}-${version}")

    fun buildNftId(transferEvent: IndexedTransferEvent): String =
        buildHashedId(
            "${transferEvent.tokenAddress}-${Numeric.parsePaddedNumberHex(transferEvent.topics[3])}"
        )
}

private fun buildHashedId(plainId: String): String = DigestUtils.sha1Hex(plainId)
