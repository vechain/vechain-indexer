package org.vechain.indexer.b3tr.richlist

enum class RichlistScope {
    /** Combined VOT3 + B3TR balance and rank. */
    ALL,

    /** B3TR balance only (excludes B3TR held by VOT3 contract). */
    B3TR,

    /** VOT3 balance only. */
    VOT3,
}
