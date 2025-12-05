package org.vechain.indexer.performance

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.roundToInt

data class PerformanceMetrics(
    val indexerName: String,
    val startBlock: Long,
    val endBlock: Long,
    val totalBlocks: Long,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val duration: Duration,
    val averageTimePerBlock: Duration,
    val blocksPerSecond: Double,
    val memoryUsageMB: MemoryUsage,
    val cpuUsage: CpuUsage? = null,
    val errors: List<String> = emptyList(),
    val nodeUrl: String,
) {
    data class MemoryUsage(
        val startUsedMemoryMB: Long,
        val endUsedMemoryMB: Long,
        val peakUsedMemoryMB: Long,
        val memoryIncreaseMB: Long,
    )

    data class CpuUsage(
        val averageLoadPercent: Double,
        val peakLoadPercent: Double,
        val processCpuTimeMs: Long,
    )

    fun printSummary() {
        val separator = "=".repeat(80)
        println(separator)
        println("Performance Test Results: $indexerName")
        println(separator)
        println("Node: $nodeUrl")
        println("Block Range: $startBlock - $endBlock ($totalBlocks blocks)")
        println("Duration: ${duration.toMillis()}ms (${duration.toSeconds()}s)")
        println("Average Time Per Block: ${averageTimePerBlock.toMillis()}ms")
        println(
            "Blocks Per Second: ${"%.2f".format(blocksPerSecond)} (${(blocksPerSecond * 3600).roundToInt()} blocks/hour)"
        )
        println("Memory Usage:")
        println("  Start: ${memoryUsageMB.startUsedMemoryMB} MB")
        println("  End: ${memoryUsageMB.endUsedMemoryMB} MB")
        println("  Peak: ${memoryUsageMB.peakUsedMemoryMB} MB")
        println("  Increase: ${memoryUsageMB.memoryIncreaseMB} MB")
        if (cpuUsage != null) {
            println("CPU Usage:")
            println("  Average Load: ${"%.2f".format(cpuUsage.averageLoadPercent)}%")
            println("  Peak Load: ${"%.2f".format(cpuUsage.peakLoadPercent)}%")
            println("  Process CPU Time: ${cpuUsage.processCpuTimeMs}ms")
        }
        if (errors.isNotEmpty()) {
            println("Errors (${errors.size}):")
            errors.take(10).forEach { println("  - $it") }
            if (errors.size > 10) {
                println("  ... and ${errors.size - 10} more errors")
            }
        }
        println(separator)
    }

    fun toCsv(): String {
        return "$indexerName,$startBlock,$endBlock,$totalBlocks,${duration.toMillis()}," +
            "${averageTimePerBlock.toMillis()},$blocksPerSecond," +
            "${memoryUsageMB.startUsedMemoryMB},${memoryUsageMB.endUsedMemoryMB}," +
            "${memoryUsageMB.peakUsedMemoryMB},${memoryUsageMB.memoryIncreaseMB},${errors.size},$nodeUrl"
    }

    companion object {
        fun csvHeader(): String {
            return "IndexerName,StartBlock,EndBlock,TotalBlocks,DurationMs," +
                "AvgTimePerBlockMs,BlocksPerSecond,StartMemoryMB,EndMemoryMB," +
                "PeakMemoryMB,MemoryIncreaseMB,ErrorCount,NodeUrl"
        }
    }
}
