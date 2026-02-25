package org.vechain.indexer.config.mongo

import com.mongodb.event.CommandFailedEvent
import com.mongodb.event.CommandListener
import com.mongodb.event.CommandStartedEvent
import com.mongodb.event.CommandSucceededEvent
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

/**
 * A [CommandListener] that tracks the sequence of commands within each MongoDB transaction session.
 *
 * On a failed `commitTransaction`, it logs the full sequence of operations that occurred within
 * that transaction (e.g., `insert(validators) -> insert(validators_archive) ->
 * commitTransaction(FAILED)`), providing visibility into exactly what the failed transaction was
 * trying to do.
 *
 * Gated by the config property `indexer.diagnostic-command-listener.enabled` (default `false`).
 */
class DiagnosticCommandListener : CommandListener {

    private val logger = LoggerFactory.getLogger(this::class.java)

    // Key: "lsid:txnNumber" -> list of command descriptions
    private val transactionCommands = ConcurrentHashMap<String, MutableList<String>>()

    override fun commandStarted(event: CommandStartedEvent) {
        val key = extractTransactionKey(event) ?: return
        val commandName = event.commandName
        val description =
            when (commandName) {
                "insert" -> "insert(${collectionName(event.command, "insert")})"
                "update" -> "update(${collectionName(event.command, "update")})"
                "delete" -> "delete(${collectionName(event.command, "delete")})"
                "find" -> "find(${collectionName(event.command, "find")})"
                "aggregate" -> "aggregate(${collectionName(event.command, "aggregate")})"
                "commitTransaction" -> "commitTransaction"
                "abortTransaction" -> "abortTransaction"
                else -> commandName
            }
        transactionCommands.computeIfAbsent(key) { mutableListOf() }.add(description)
    }

    override fun commandSucceeded(event: CommandSucceededEvent) {
        if (event.commandName == "commitTransaction" || event.commandName == "abortTransaction") {
            val key = extractTransactionKeyFromRequestId(event.requestId)
            if (key != null) {
                transactionCommands.remove(key)
            }
        }
    }

    override fun commandFailed(event: CommandFailedEvent) {
        if (event.commandName == "commitTransaction") {
            val key = extractTransactionKeyFromRequestId(event.requestId)
            val commands = if (key != null) transactionCommands.remove(key) else null
            val sequence =
                if (commands != null) {
                    commands.joinToString(" -> ") + " -> commitTransaction(FAILED)"
                } else {
                    "commitTransaction(FAILED) [no tracked commands]"
                }
            logger.error(
                "Transaction commit failed. Command sequence: {}. Failure: {}",
                sequence,
                event.throwable.message,
            )
        } else {
            // For non-commit failures, just annotate the tracked sequence
            val key = extractTransactionKeyFromRequestId(event.requestId)
            if (key != null) {
                val commands = transactionCommands[key]
                if (commands != null && commands.isNotEmpty()) {
                    val last = commands.removeAt(commands.size - 1)
                    commands.add("$last(FAILED: ${event.throwable.message})")
                }
            }
        }
    }

    // Maps requestId -> transactionKey for correlation between start and succeeded/failed events
    private val requestIdToKey = ConcurrentHashMap<Int, String>()

    private fun collectionName(command: org.bson.BsonDocument, key: String): String =
        try {
            command.getString(key).value
        } catch (_: Exception) {
            "?"
        }

    private fun extractTransactionKey(event: CommandStartedEvent): String? {
        val command = event.command
        if (!command.containsKey("lsid") || !command.containsKey("txnNumber")) return null
        val lsid = command.getDocument("lsid")
        val txnNumber = command.getInt64("txnNumber").value
        val sessionId =
            if (lsid.containsKey("id")) {
                lsid.getBinary("id").asUuid().toString()
            } else {
                lsid.toJson()
            }
        val key = "$sessionId:$txnNumber"
        requestIdToKey[event.requestId] = key
        return key
    }

    private fun extractTransactionKeyFromRequestId(requestId: Int): String? =
        requestIdToKey.remove(requestId)
}
