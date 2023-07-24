package org.vechain.indexer.model.rest

class Views {

    /** Public serialization view of POJOs */
    open class Public

    /** Extends the public view with internal use only fields (internal APIs, tests, etc.) */
    open class Internal : Public()
}
