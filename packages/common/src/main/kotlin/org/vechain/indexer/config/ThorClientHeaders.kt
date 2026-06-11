package org.vechain.indexer.config

fun thorClientHeaders(bypassKey: String): Array<Pair<String, Any>> {
    val normalizedKey = bypassKey.trim()
    val headers = mutableListOf<Pair<String, Any>>("X-Project-Id" to "veworld-indexer")
    if (normalizedKey.isNotEmpty()) {
        headers.add("x-rate-limit-bypass" to normalizedKey)
    }
    return headers.toTypedArray()
}
