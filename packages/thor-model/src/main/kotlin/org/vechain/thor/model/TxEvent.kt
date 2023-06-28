package org.vechain.thor.model

import org.web3j.protocol.core.methods.response.Log

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
