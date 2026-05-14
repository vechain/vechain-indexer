package org.vechain.indexer.validator.scheduler

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Long-running co-process that wraps the Go `thor-scheduler` binary. The binary speaks line-
 * delimited JSON over stdin/stdout: see `tools/thor-scheduler/README.md`.
 *
 * One process is spawned at startup and held for the indexer's lifetime. A mutex serializes
 * requests so we can correlate by line position; if we ever need concurrent requests we can switch
 * to id-keyed correlation (the protocol already echoes ids).
 */
@Profile("validator-v2", "validator")
@Component
class ThorSchedulerProcess(
    @param:Value("\${validator-v2.scheduler-binary:./tools/thor-scheduler/thor-scheduler}")
    private val binaryPath: String
) : InitializingBean, DisposableBean {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val mapper = jacksonObjectMapper()
    private val mutex = Mutex()
    private val nextId = AtomicLong(0)

    private lateinit var process: Process
    private lateinit var writer: BufferedWriter
    private lateinit var reader: BufferedReader

    override fun afterPropertiesSet() {
        process = ProcessBuilder(binaryPath).redirectErrorStream(false).start()
        writer = process.outputStream.bufferedWriter()
        reader = process.inputStream.bufferedReader()
        Thread {
                process.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { logger.warn("thor-scheduler: {}", it) }
                }
            }
            .apply {
                isDaemon = true
                name = "thor-scheduler-stderr"
                start()
            }
        logger.info("Started thor-scheduler subprocess: {}", binaryPath)
    }

    override fun destroy() {
        if (::process.isInitialized && process.isAlive) {
            process.destroy()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
        }
    }

    /**
     * Returns the verified VRF Beta value for the given raw RLP-encoded block header.
     *
     * Returns null for blocks that don't have a VRF proof (pre-VIP-193, genesis) — these have no
     * usable seed contribution.
     */
    suspend fun beta(rawHeaderHex: String): String? =
        call(mapOf("op" to "beta", "rawHeader" to rawHeaderHex)).beta?.takeIf { it.isNotEmpty() }

    /**
     * Returns the ordered list of active validators scheduled to propose blocks starting at slot 0
     * (the slot immediately after the parent block). Determined by (seed, parentBlockNumber,
     * proposer set with weights and active flags).
     */
    suspend fun schedule(
        seedHex: String,
        parentBlockNumber: Long,
        proposers: List<Proposer>,
    ): List<String> =
        call(
                mapOf(
                    "op" to "schedule",
                    "seed" to seedHex,
                    "parentBlockNumber" to parentBlockNumber,
                    "proposers" to proposers,
                )
            )
            .schedule ?: emptyList()

    private suspend fun call(body: Map<String, Any?>): Response =
        withContext(Dispatchers.IO) {
            val id = nextId.incrementAndGet()
            val req = body + ("id" to id)
            mutex.withLock {
                writer.write(mapper.writeValueAsString(req))
                writer.newLine()
                writer.flush()
                val line =
                    reader.readLine()
                        ?: throw IllegalStateException("thor-scheduler subprocess closed stdout")
                val resp = mapper.readValue<Response>(line)
                if (resp.error != null) {
                    throw IllegalStateException("thor-scheduler error: ${resp.error}")
                }
                resp
            }
        }

    data class Proposer(val address: String, val weight: Long, val active: Boolean)

    data class Response(
        val id: Long? = null,
        val beta: String? = null,
        val schedule: List<String>? = null,
        val error: String? = null,
    )
}
