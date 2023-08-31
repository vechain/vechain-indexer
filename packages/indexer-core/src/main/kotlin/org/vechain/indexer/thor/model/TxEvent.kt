package org.vechain.indexer.thor.model

import com.fasterxml.jackson.annotation.JsonView
import org.web3j.protocol.core.methods.response.Log

@JsonView(Views.Expanded::class)
data class TxEvent(val address: String, val topics: List<String>, val data: String) {

    /** This can be used for decoding events with Web3J */
    fun toLog(): Log {
        val log = Log()
        log.address = address
        log.topics = topics
        log.data = data
        return log
    }
}
