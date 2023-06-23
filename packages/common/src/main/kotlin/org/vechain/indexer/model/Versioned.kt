package org.vechain.indexer.model

interface Versioned {
    //The version of this document. Starts from 1 and increments on every update.
    val version: Int
}