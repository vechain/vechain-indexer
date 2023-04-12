package org.vechain.indexer.utils

import org.vechain.indexer.model.ExecuteCodeResponse

object TransactionUtils {
    fun isSuccessWithData(res: ExecuteCodeResponse): Boolean {
        return res.reverted == false &&
                res.vmError.isNullOrEmpty() &&
                res.data != null &&
                res.data != "0x"
    }
}