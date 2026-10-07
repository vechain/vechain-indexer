package org.vechain.indexer.exception

import org.springframework.http.HttpStatus

/** A window ending past the newest indexed block has no complete answer yet, only a later one. */
class WindowNotIndexedException(val to: Long?, val indexedThrough: Long?) :
    AbstractHttpException(
        if (indexedThrough == null) "nothing is indexed yet"
        else "to=$to is beyond indexedThrough=$indexedThrough",
        HttpStatus.CONFLICT,
    ) {
    companion object {
        const val INDEXED_THROUGH_HEADER = "X-Indexed-Through"
    }
}
