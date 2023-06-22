package org.vechain.indexer.utils

import org.apache.commons.codec.digest.DigestUtils

object IdUtils {
    fun buildHashedId(plainId: String) = DigestUtils.sha1Hex(plainId)
}