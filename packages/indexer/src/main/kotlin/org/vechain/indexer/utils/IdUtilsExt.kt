package org.vechain.indexer.utils

import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsString

fun buildNftId(transferEvent: IndexedEvent): String {
    val address =
        transferEvent.address
            ?: throw IllegalArgumentException("transferEvent.address cannot be null")
    val tokenId =
        transferEvent.params.getAsString("tokenId")
            ?: throw IllegalArgumentException("transferEvent.params.tokenId cannot be null")
    return IdUtils.generateId(address, tokenId)
}
