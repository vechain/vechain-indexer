package org.vechain.indexer.validator.scheduler

import java.io.File
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Integration test that spawns the real `thor-scheduler` Go binary. Skipped automatically if the
 * binary hasn't been built locally — run `make build` in `tools/thor-scheduler` first.
 */
class ThorSchedulerProcessTest {

    private lateinit var process: ThorSchedulerProcess

    @BeforeEach
    fun setUp() {
        val binary = findBinary()
        assumeTrue(
            binary != null,
            "thor-scheduler binary not found; run `make build` in tools/thor-scheduler",
        )
        process = ThorSchedulerProcess(binaryPath = binary!!.absolutePath)
        process.afterPropertiesSet()
    }

    @AfterEach
    fun tearDown() {
        if (::process.isInitialized) process.destroy()
    }

    @Test
    fun `schedule produces deterministic ordering for fixed seed`() {
        runBlocking {
            val proposers =
                listOf(
                    ThorSchedulerProcess.Proposer(
                        "0x1111111111111111111111111111111111111111",
                        100,
                        true,
                    ),
                    ThorSchedulerProcess.Proposer(
                        "0x2222222222222222222222222222222222222222",
                        200,
                        true,
                    ),
                    ThorSchedulerProcess.Proposer(
                        "0x3333333333333333333333333333333333333333",
                        50,
                        false,
                    ),
                    ThorSchedulerProcess.Proposer(
                        "0x4444444444444444444444444444444444444444",
                        150,
                        true,
                    ),
                )

            val first = process.schedule("0xdeadbeefcafe", 1000, proposers)
            val second = process.schedule("0xdeadbeefcafe", 1000, proposers)

            assertThat(first).isEqualTo(second)
            assertThat(first).hasSize(3) // inactive excluded
            assertThat(first).doesNotContain("0x3333333333333333333333333333333333333333")
            assertThat(first)
                .containsExactlyInAnyOrder(
                    "0x1111111111111111111111111111111111111111",
                    "0x2222222222222222222222222222222222222222",
                    "0x4444444444444444444444444444444444444444",
                )
        }
    }

    @Test
    fun `different parent block numbers produce different schedules`() {
        runBlocking {
            val proposers =
                listOf(
                    ThorSchedulerProcess.Proposer(
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        100,
                        true,
                    ),
                    ThorSchedulerProcess.Proposer(
                        "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        100,
                        true,
                    ),
                    ThorSchedulerProcess.Proposer(
                        "0xcccccccccccccccccccccccccccccccccccccccc",
                        100,
                        true,
                    ),
                    ThorSchedulerProcess.Proposer(
                        "0xdddddddddddddddddddddddddddddddddddddddd",
                        100,
                        true,
                    ),
                )

            val a = process.schedule("0x00", 100, proposers)
            val b = process.schedule("0x00", 101, proposers)

            assertThat(a).isNotEqualTo(b)
        }
    }

    @Test
    fun `empty proposers returns empty schedule`() {
        runBlocking {
            val result = process.schedule("0x00", 100, emptyList())
            assertThat(result).isEmpty()
        }
    }

    @Test
    fun `process survives a malformed beta request`() {
        runBlocking {
            val ex = runCatching { process.beta("0xc0") }.exceptionOrNull()
            assertThat(ex).isInstanceOf(IllegalStateException::class.java)

            val schedule =
                process.schedule(
                    "0x00",
                    1,
                    listOf(
                        ThorSchedulerProcess.Proposer(
                            "0x1111111111111111111111111111111111111111",
                            1,
                            true,
                        )
                    ),
                )
            assertThat(schedule).hasSize(1)
        }
    }

    private fun findBinary(): File? {
        val candidates =
            listOf(
                "tools/thor-scheduler/thor-scheduler",
                "../../tools/thor-scheduler/thor-scheduler",
                "../../../tools/thor-scheduler/thor-scheduler",
            )
        return candidates.map { File(it) }.firstOrNull { it.exists() && it.canExecute() }
    }
}
