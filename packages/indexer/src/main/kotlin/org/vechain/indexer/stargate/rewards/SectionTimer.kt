package org.vechain.indexer.stargate.rewards

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/** Temporary profiling helper: accumulates wall time per named section and prints periodically. */
object SectionTimer {
    private const val REPORT_INTERVAL_MS = 10_000L

    private val nanos = ConcurrentHashMap<String, LongAdder>()
    private val calls = ConcurrentHashMap<String, LongAdder>()
    private val lastReport = AtomicLong(System.currentTimeMillis())

    inline fun <T> time(section: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            record(section, System.nanoTime() - start)
        }
    }

    fun record(section: String, elapsedNanos: Long) {
        nanos.computeIfAbsent(section) { LongAdder() }.add(elapsedNanos)
        calls.computeIfAbsent(section) { LongAdder() }.increment()
    }

    /** Prints the cumulative table if [REPORT_INTERVAL_MS] has passed since the last print. */
    fun maybeReport(totalSection: String = "processEntry") {
        val now = System.currentTimeMillis()
        val last = lastReport.get()
        if (now - last < REPORT_INTERVAL_MS || !lastReport.compareAndSet(last, now)) return

        val total = nanos[totalSection]?.sum()?.coerceAtLeast(1) ?: 1
        val rows =
            nanos.entries
                .map { (name, n) -> Triple(name, n.sum(), calls[name]?.sum() ?: 0) }
                .sortedByDescending { it.second }
        val sb = StringBuilder("\n==== TokenRewardService timings (cumulative) ====\n")
        sb.append(
            String.format(
                "%-40s %12s %10s %12s %7s%n",
                "section",
                "total ms",
                "calls",
                "avg µs",
                "%",
            )
        )
        rows.forEach { (name, n, c) ->
            sb.append(
                String.format(
                    "%-40s %12.1f %10d %12.1f %6.1f%%%n",
                    name,
                    n / 1e6,
                    c,
                    if (c == 0L) 0.0 else n / 1e3 / c,
                    n * 100.0 / total,
                )
            )
        }
        println(sb)
    }

    fun reset() {
        nanos.clear()
        calls.clear()
    }
}
