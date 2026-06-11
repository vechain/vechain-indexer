package org.vechain.indexer.config

fun thorClientHeaders(bypassKey: String): Array<Pair<String, Any>> {
    val headers = mutableListOf<Pair<String, Any>>("X-Project-Id" to "veworld-indexer")
    if (bypassKey.isNotBlank()) {
        headers.add("x-rate-limit-bypass" to bypassKey)
    }
    return headers.toTypedArray()
}
