package org.vechain.indexer.thor.model

class Views {

    /** Public serialization view of POJOs */
    open class Public

    /** Extends the public view with internal use only fields (internal APIs, tests, etc.) */
    open class Internal : Public()

    /** Extends the public view with additional fields required for a more detailed model */
    open class Expanded : Public()
}
