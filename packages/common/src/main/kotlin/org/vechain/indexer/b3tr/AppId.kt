package org.vechain.indexer.b3tr

import kotlin.text.matches

data class AppId(val value: String) {

    companion object {
        const val REGEX = "^(0x)?[0-9a-fA-F]{64}\$"
    }

    fun isValid(): Boolean {
        return value.matches(Regex(REGEX))
    }
}
