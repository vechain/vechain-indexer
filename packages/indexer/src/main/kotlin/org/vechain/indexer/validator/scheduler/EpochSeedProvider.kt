package org.vechain.indexer.validator.scheduler

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.thor.model.Block

/**
 * Supplies the epoch seed to drive the PoS proposer schedule, mirroring
 * `scheduler/seed.go::Generate`: the seed for the current block is the verified VRF `Beta` value of
 * the first block of the *previous* seeder epoch.
 *
 * Implementations are expected to fetch and cache; the seed only changes once per `seederInterval`
 * blocks (~1 day on a 10-second block).
 */
interface EpochSeedProvider {
    suspend fun seedFor(block: Block): ByteArray?
}

/**
 * Production implementation: fetches the seed block's raw RLP header via Thor's `?raw=true`
 * endpoint and asks the thor-scheduler co-process to verify its VRF proof and return Beta.
 *
 * Results are cached per seed-block number; in steady state we hit Thor exactly once per epoch.
 */
@Profile("validator-v2")
@Component
class SubprocessEpochSeedProvider(
    private val scheduler: ThorSchedulerProcess,
    @param:Value("\${thor.url}") private val thorUrl: String,
) : EpochSeedProvider {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val seedCache = ConcurrentHashMap<Long, ByteArray>()

    override suspend fun seedFor(block: Block): ByteArray? {
        val seedBlockNum = seedBlockNumberFor(block.number) ?: return null
        seedCache[seedBlockNum]?.let {
            return it
        }
        return fetchAndCache(seedBlockNum)
    }

    private suspend fun fetchAndCache(seedBlockNum: Long): ByteArray? {
        val rawHex = fetchRawHeader(seedBlockNum) ?: return null
        val betaHex = scheduler.beta(rawHex) ?: return null
        val bytes = hexToBytes(betaHex)
        seedCache[seedBlockNum] = bytes
        logger.info("Cached epoch seed from block {} ({} bytes)", seedBlockNum, bytes.size)
        return bytes
    }

    private suspend fun fetchRawHeader(blockNum: Long): String? =
        withContext(Dispatchers.IO) {
            val (_, _, result) =
                Fuel.get("$thorUrl/blocks/$blockNum", listOf("raw" to true)).response()
            when (result) {
                is Result.Success -> {
                    val body = result.get().toString(Charsets.UTF_8)
                    parseRawHex(body)
                }
                is Result.Failure -> {
                    logger.warn(
                        "Failed to fetch raw block {} for seed: {}",
                        blockNum,
                        result.error.message,
                    )
                    null
                }
            }
        }

    /** Thor's `?raw=true` endpoint returns `{"raw":"0x..."}`. */
    private fun parseRawHex(body: String): String? {
        val match = RAW_REGEX.find(body) ?: return null
        return match.groupValues[1]
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.removePrefix("0x")
        return ByteArray(clean.length / 2) { i ->
            ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16))
                .toByte()
        }
    }

    /**
     * For block N, the seed comes from block `(epoch - 1) * SEEDER_INTERVAL` where `epoch = N /
     * SEEDER_INTERVAL`. The first two epochs have no seed.
     */
    private fun seedBlockNumberFor(blockNumber: Long): Long? {
        val epoch = blockNumber / SEEDER_INTERVAL
        if (epoch <= 1) return null
        return (epoch - 1) * SEEDER_INTERVAL
    }

    companion object {
        private const val SEEDER_INTERVAL = 8640L
        private val RAW_REGEX = Regex("\"raw\"\\s*:\\s*\"(0x[0-9a-fA-F]+)\"")
    }
}
