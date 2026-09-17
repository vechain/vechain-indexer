package org.vechain.indexer.postgres

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IndexBuildBudgetTest {

    @Test
    fun `no more builds run at once than the budget has permits`() {
        val budget = IndexBuildBudget(2)
        val running = AtomicInteger()
        val peak = AtomicInteger()
        val done = CountDownLatch(8)
        val pool = Executors.newFixedThreadPool(8)

        repeat(8) {
            pool.submit {
                budget.withPermit {
                    peak.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                    Thread.sleep(50)
                    running.decrementAndGet()
                }
                done.countDown()
            }
        }

        assertTrue(done.await(30, TimeUnit.SECONDS), "the permits were not released")
        pool.shutdown()
        assertEquals(2, peak.get())
        assertEquals(0, running.get())
    }

    @Test
    fun `a failed build gives its permit back`() {
        val budget = IndexBuildBudget(1)

        runCatching { budget.withPermit { error("the connection went away") } }

        assertEquals("ok", budget.withPermit { "ok" })
    }
}
