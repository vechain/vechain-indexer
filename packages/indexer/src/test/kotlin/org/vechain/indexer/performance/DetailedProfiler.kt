package org.vechain.indexer.performance

import kotlin.math.roundToInt

/**
 * Detailed profiling utility to measure timing of specific operations with MILLISECOND precision
 * (uses System.nanoTime() instead of Instant)
 */
class DetailedProfiler {

    private val timings = mutableMapOf<String, MutableList<Double>>()
    private val currentTimers = mutableMapOf<String, Long>()

    /** Start timing an operation */
    fun start(operationName: String) {
        currentTimers[operationName] = System.nanoTime()
    }

    /** Stop timing an operation and record the duration in milliseconds */
    fun stop(operationName: String) {
        val endTime = System.nanoTime()
        val startTime = currentTimers.remove(operationName)
        if (startTime != null) {
            val durationMs =
                (endTime - startTime) / 1_000_000.0 // Convert nanoseconds to milliseconds
            timings.getOrPut(operationName) { mutableListOf() }.add(durationMs)
        }
    }

    /** Time a block of code */
    inline fun <T> time(operationName: String, block: () -> T): T {
        start(operationName)
        return try {
            block()
        } finally {
            stop(operationName)
        }
    }

    /** Time a suspend block of code */
    suspend inline fun <T> timeSuspend(
        operationName: String,
        crossinline block: suspend () -> T,
    ): T {
        start(operationName)
        return try {
            block()
        } finally {
            stop(operationName)
        }
    }

    /** Get profiling results in milliseconds */
    fun getResults(): ProfilingResults {
        val operations =
            timings
                .map { (name, durations) ->
                    OperationStats(
                        name = name,
                        callCount = durations.size,
                        totalTimeMs = durations.sum(),
                        avgTimeMs = if (durations.isNotEmpty()) durations.average() else 0.0,
                        minTimeMs = durations.minOrNull() ?: 0.0,
                        maxTimeMs = durations.maxOrNull() ?: 0.0,
                        percentile95Ms = calculatePercentile(durations, 95.0),
                        percentile99Ms = calculatePercentile(durations, 99.0),
                    )
                }
                .sortedByDescending { it.totalTimeMs }

        return ProfilingResults(operations)
    }

    private fun calculatePercentile(values: List<Double>, percentile: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = ((percentile / 100.0) * sorted.size).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    fun reset() {
        timings.clear()
        currentTimers.clear()
    }
}

data class OperationStats(
    val name: String,
    val callCount: Int,
    val totalTimeMs: Double,
    val avgTimeMs: Double,
    val minTimeMs: Double,
    val maxTimeMs: Double,
    val percentile95Ms: Double,
    val percentile99Ms: Double,
)

data class ProfilingResults(val operations: List<OperationStats>) {
    fun printSummary() {
        println("\n" + "=".repeat(110))
        println("DETAILED PROFILING RESULTS (Millisecond Precision)")
        println("=".repeat(110))
        println(
            String.format(
                "%-40s %8s %14s %12s %12s %12s %10s %10s",
                "Operation",
                "Calls",
                "Total (ms)",
                "Avg (ms)",
                "Min (ms)",
                "Max (ms)",
                "P95 (ms)",
                "P99 (ms)",
            )
        )
        println("-".repeat(110))

        val totalTime = operations.sumOf { it.totalTimeMs }

        operations.forEach { op ->
            val percentage = if (totalTime > 0) (op.totalTimeMs / totalTime * 100) else 0.0
            println(
                String.format(
                    "%-40s %8d %14.3f %12.3f %12.3f %12.3f %10.3f %10.3f  (%.1f%%)",
                    op.name.take(40),
                    op.callCount,
                    op.totalTimeMs,
                    op.avgTimeMs,
                    op.minTimeMs,
                    op.maxTimeMs,
                    op.percentile95Ms,
                    op.percentile99Ms,
                    percentage,
                )
            )
        }

        println("-".repeat(110))
        println(String.format("%-40s %8s %14.3f ms", "TOTAL", "", totalTime))
        println("=".repeat(110))

        // Print bottleneck analysis
        if (operations.isNotEmpty()) {
            println("\n🔍 BOTTLENECK ANALYSIS:")
            val top5 = operations.take(5)
            top5.forEachIndexed { index, op ->
                val percentage = if (totalTime > 0) (op.totalTimeMs / totalTime * 100) else 0.0
                println(
                    "  ${index + 1}. ${op.name}: ${"%.3f".format(op.totalTimeMs)}ms (${percentage.roundToInt()}% of total, avg: ${"%.3f".format(op.avgTimeMs)}ms per call)"
                )
            }

            // Recommendations
            println("\n💡 RECOMMENDATIONS:")
            top5.forEach { op ->
                when {
                    op.name.contains("MongoDB", ignoreCase = true) ||
                        op.name.contains("save", ignoreCase = true) ||
                        op.name.contains("repository", ignoreCase = true) -> {
                        println(
                            "  • ${op.name}: ${"%.3f".format(op.avgTimeMs)}ms avg - Consider batch writing or connection pooling"
                        )
                    }
                    op.name.contains("Thor", ignoreCase = true) ||
                        op.name.contains("fetch", ignoreCase = true) ||
                        op.name.contains("call", ignoreCase = true) -> {
                        println(
                            "  • ${op.name}: ${"%.3f".format(op.avgTimeMs)}ms avg - Consider caching or batch requests"
                        )
                    }
                    op.avgTimeMs > 1.0 -> { // > 1ms average
                        println(
                            "  • ${op.name}: avg time is ${"%.3f".format(op.avgTimeMs)}ms - Investigate logic complexity"
                        )
                    }
                }
            }
        }
        println()
    }

    fun toCsv(): String {
        val header = "Operation,Calls,TotalMs,AvgMs,MinMs,MaxMs,P95Ms,P99Ms"
        val rows =
            operations.joinToString("\n") { op ->
                "${op.name},${op.callCount},${"%.3f".format(op.totalTimeMs)},${"%.3f".format(op.avgTimeMs)},${"%.3f".format(op.minTimeMs)},${"%.3f".format(op.maxTimeMs)},${"%.3f".format(op.percentile95Ms)},${"%.3f".format(op.percentile99Ms)}"
            }
        return "$header\n$rows"
    }
}
