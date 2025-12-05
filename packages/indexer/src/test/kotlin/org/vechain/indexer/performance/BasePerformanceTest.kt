package org.vechain.indexer.performance

import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.TestPropertySource
import org.vechain.indexer.Indexer
import org.vechain.indexer.SimpleBlockIndexerCoordinator
import org.vechain.indexer.Status
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.config.IndexManager
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.version.IndexerVersionService

@SpringBootTest(
    properties =
        [
            "spring.main.lazy-initialization=true",
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration",
        ]
)
@TestPropertySource(locations = ["classpath:application-test.properties"])
abstract class BasePerformanceTest {

    @Autowired lateinit var thorClient: ThorClient

    @Autowired(required = false) lateinit var businessEventProperties: BusinessEventProperties

    protected lateinit var mockIndexerVersionService: IndexerVersionService

    @Value("\${thor.url}") lateinit var thorUrl: String

    // Mock IndexManager to prevent it from starting all indexers
    @MockBean private lateinit var indexManager: IndexManager

    @BeforeEach
    fun setupBase() {
        mockIndexerVersionService = mockk(relaxed = true)
        every { mockIndexerVersionService.getLastProcessedBlock(any()) } returns null
    }

    protected data class PerformanceTestConfig(
        val indexerName: String,
        val startBlock: Long,
        val blockCount: Int,
        val warmupBlocks: Int = 10,
    )

    protected fun runPerformanceTest(
        config: PerformanceTestConfig,
        indexerBuilder: (startBlock: Long) -> Indexer,
        profiler: DetailedProfiler? = null,
    ): PerformanceMetrics {
        println("Starting performance test for ${config.indexerName}")
        println("Configuration:")
        println("  Start Block: ${config.startBlock}")
        println("  Block Count: ${config.blockCount}")
        println("  Warmup Blocks: ${config.warmupBlocks}")
        println("  Thor URL: $thorUrl")

        val errors = mutableListOf<String>()
        val runtime = Runtime.getRuntime()
        val osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
        val processCpuBean = osBean as? com.sun.management.OperatingSystemMXBean

        // Force GC before test
        System.gc()
        Thread.sleep(100)

        val startMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        var peakMemory = startMemory

        // CPU tracking
        val cpuLoadSamples = mutableListOf<Double>()
        val startCpuTime = processCpuBean?.processCpuTime ?: 0L

        // Warmup phase
        if (config.warmupBlocks > 0) {
            println("Running warmup phase (${config.warmupBlocks} blocks)...")
            try {
                val warmupBlocks =
                    fetchBlocks(
                        config.startBlock - config.warmupBlocks,
                        config.warmupBlocks,
                        errors,
                    )
                val warmupIndexer = indexerBuilder(config.startBlock - config.warmupBlocks)
                runBlocking { SimpleBlockIndexerCoordinator.launch(warmupIndexer, warmupBlocks) }
            } catch (e: Exception) {
                println("Warmup failed (continuing anyway): ${e.message}")
            }
            System.gc()
            Thread.sleep(100)
        }

        // Actual test
        println("Fetching ${config.blockCount} blocks starting from ${config.startBlock}...")
        val blocks = fetchBlocks(config.startBlock, config.blockCount, errors)

        println("Creating indexer...")
        val indexer = indexerBuilder(config.startBlock)

        // Track memory and CPU during execution
        val resourceTracker = Thread {
            while (!Thread.currentThread().isInterrupted) {
                val currentMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                if (currentMemory > peakMemory) {
                    peakMemory = currentMemory
                }

                // Sample CPU load more frequently for better accuracy
                if (processCpuBean != null) {
                    val cpuLoad = processCpuBean.processCpuLoad * 100.0
                    if (cpuLoad >= 0) { // -1 means not available
                        cpuLoadSamples.add(cpuLoad)
                    }
                }

                Thread.sleep(50) // Sample every 50ms for better CPU readings
            }
        }

        println("Starting performance test...")
        val startTime = LocalDateTime.now()
        resourceTracker.start()

        try {
            if (profiler != null) {
                profiler.time("Total Indexing Time") {
                    runBlocking { SimpleBlockIndexerCoordinator.launch(indexer, blocks) }
                }
            } else {
                runBlocking { SimpleBlockIndexerCoordinator.launch(indexer, blocks) }
            }
        } catch (e: Exception) {
            errors.add("Fatal error during indexing: ${e.message}")
            e.printStackTrace()
        }

        val endTime = LocalDateTime.now()
        resourceTracker.interrupt()
        resourceTracker.join()

        val endMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val endCpuTime = processCpuBean?.processCpuTime ?: 0L
        val duration = Duration.between(startTime, endTime)
        val averageTimePerBlock = duration.dividedBy(blocks.size.toLong())
        val blocksPerSecond =
            if (duration.toMillis() > 0) {
                blocks.size.toDouble() / (duration.toMillis().toDouble() / 1000.0)
            } else {
                0.0
            }

        // Calculate CPU metrics
        val cpuUsage =
            if (cpuLoadSamples.isNotEmpty()) {
                PerformanceMetrics.CpuUsage(
                    averageLoadPercent = cpuLoadSamples.average(),
                    peakLoadPercent = cpuLoadSamples.maxOrNull() ?: 0.0,
                    processCpuTimeMs =
                        (endCpuTime - startCpuTime) /
                            1_000_000, // Convert nanoseconds to milliseconds
                )
            } else {
                null
            }

        val metrics =
            PerformanceMetrics(
                indexerName = config.indexerName,
                startBlock = config.startBlock,
                endBlock = config.startBlock + config.blockCount - 1,
                totalBlocks = blocks.size.toLong(),
                startTime = startTime,
                endTime = endTime,
                duration = duration,
                averageTimePerBlock = averageTimePerBlock,
                blocksPerSecond = blocksPerSecond,
                memoryUsageMB =
                    PerformanceMetrics.MemoryUsage(
                        startUsedMemoryMB = startMemory,
                        endUsedMemoryMB = endMemory,
                        peakUsedMemoryMB = peakMemory,
                        memoryIncreaseMB = endMemory - startMemory,
                    ),
                cpuUsage = cpuUsage,
                errors = errors,
                nodeUrl = thorUrl,
            )

        metrics.printSummary()

        // Print detailed profiling results if profiler was provided
        if (profiler != null) {
            val results = profiler.getResults()
            results.printSummary()

            // Export to CSV
            val csvFile =
                "profiling-${config.indexerName.lowercase().replace(" ", "-")}-${System.currentTimeMillis()}.csv"
            java.io.File(csvFile).writeText(results.toCsv())
            println("📊 Detailed profiling results exported to: $csvFile\n")
        }

        return metrics
    }

    private fun fetchBlocks(
        startBlock: Long,
        count: Int,
        errors: MutableList<String>,
    ): List<Block> {
        val blocks = mutableListOf<Block>()
        var currentBlock = startBlock

        for (i in 0 until count) {
            try {
                val block = runBlocking { thorClient.getBlock(currentBlock) }
                if (block != null) {
                    blocks.add(block)
                    if ((i + 1) % 100 == 0) {
                        println("Fetched ${i + 1}/$count blocks...")
                    }
                } else {
                    errors.add("Block $currentBlock returned null")
                }
            } catch (e: Exception) {
                errors.add("Failed to fetch block $currentBlock: ${e.message}")
            }
            currentBlock++
        }

        println("Successfully fetched ${blocks.size}/$count blocks")
        return blocks
    }

    protected fun waitForStatus(
        indexer: Indexer,
        expectedStatus: Status,
        timeoutSeconds: Long = 60,
    ) {
        val startTime = System.currentTimeMillis()
        while (indexer.getStatus() != expectedStatus) {
            if (System.currentTimeMillis() - startTime > timeoutSeconds * 1000) {
                throw RuntimeException(
                    "Timeout waiting for status $expectedStatus, current status: ${indexer.getStatus()}"
                )
            }
            Thread.sleep(100)
        }
    }
}
