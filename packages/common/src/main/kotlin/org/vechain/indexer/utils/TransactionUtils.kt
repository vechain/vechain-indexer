package org.vechain.indexer.utils

import org.vechain.indexer.model.rest.ExecuteCodeResponse

object TransactionUtils {

    const val REGEX = "^(0x)?[0-9a-fA-F]{64}\$"

    fun isIdValid(id: String?): Boolean {
        return id?.matches(Regex(REGEX)) ?: false
    }

    fun isSuccessWithData(res: ExecuteCodeResponse): Boolean {
        return !res.reverted &&
            res.vmError.isNullOrEmpty() &&
            res.data.startsWith("0x") &&
            res.data.length > 2
    }
}
