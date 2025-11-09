package org.vechain.indexer.utils

import org.apache.commons.codec.digest.DigestUtils

object IdUtils {
    fun generateId(vararg inputs: String): String = DigestUtils.sha1Hex(inputs.joinToString("-"))
}
