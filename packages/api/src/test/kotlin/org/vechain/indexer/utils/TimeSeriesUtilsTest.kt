package org.vechain.indexer.utils

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.vechain.indexer.model.IndexedDocument
import org.vechain.indexer.model.TimeSeriesRecord
import strikt.api.expect
import strikt.assertions.*

internal class TimeSeriesUtilsTest {

    // Dummy implementation for IndexedDocument
    data class DummyDoc(
        override val blockId: String = "dummy",
        override val blockTimestamp: Long,
        override val blockNumber: Long = 0L,
        val value: Int,
    ) : IndexedDocument

    @Test
    fun `sparsify returns empty for empty input`() {
        expect { that(TimeSeriesUtils.sparsify<Int>(emptyList())).isEmpty() }
    }

    @Test
    fun `sparsify returns single record for single input`() {
        val input = listOf(TimeSeriesRecord(1L, 42))
        expect { that(TimeSeriesUtils.sparsify(input)).isEqualTo(input) }
    }

    @Test
    fun `sparsify removes consecutive duplicates except last`() {
        val input =
            listOf(
                TimeSeriesRecord(1L, 1),
                TimeSeriesRecord(2L, 1),
                TimeSeriesRecord(3L, 2),
                TimeSeriesRecord(4L, 2),
                TimeSeriesRecord(5L, 3),
            )
        val expected =
            listOf(TimeSeriesRecord(1L, 1), TimeSeriesRecord(3L, 2), TimeSeriesRecord(5L, 3))
        expect { that(TimeSeriesUtils.sparsify(input)).isEqualTo(expected) }
    }

    @Test
    fun `sparsify should always maintain the last record`() {
        val input =
            listOf(
                TimeSeriesRecord(1L, 1),
                TimeSeriesRecord(2L, 1),
                TimeSeriesRecord(3L, 2),
                TimeSeriesRecord(4L, 2),
                TimeSeriesRecord(5L, 2),
            )
        val expected =
            listOf(TimeSeriesRecord(1L, 1), TimeSeriesRecord(3L, 2), TimeSeriesRecord(5L, 2))
        expect { that(TimeSeriesUtils.sparsify(input)).isEqualTo(expected) }
    }

    @Test
    fun `sparsify throws on unsorted input`() {
        val input = listOf(TimeSeriesRecord(2L, 1), TimeSeriesRecord(1L, 2))
        assertThrows<IllegalStateException> { TimeSeriesUtils.sparsify(input) }
    }

    @Test
    fun `getHistoricTimeSeries returns empty for no data`() {
        val result =
            TimeSeriesUtils.getHistoricTimeSeries(
                after = 10L,
                before = 20L,
                findByBlockTimestampBetween = { _, _ -> emptyList<DummyDoc>() },
                findLatestBeforeOrAtBlockTimestamp = { _ -> null },
                valueExtractor = { it.value },
            )
        expect { that(result).isEmpty() }
    }

    @Test
    fun `getHistoricTimeSeries includes bookends and sparsifies`() {
        val docs =
            listOf(
                DummyDoc("0x01", 12, 1, 1),
                DummyDoc("0x02", 15, 2, 1),
                DummyDoc("0x03", 18, 3, 2),
            )
        val result =
            TimeSeriesUtils.getHistoricTimeSeries(
                after = 10L,
                before = 20L,
                findByBlockTimestampBetween = { _, _ -> docs },
                findLatestBeforeOrAtBlockTimestamp = { ts ->
                    if (ts == 10L) DummyDoc("0x01", 10, 1, 0) else null
                },
                valueExtractor = { it.value },
            )
        expect {
            that(result)
                .isEqualTo(
                    listOf(
                        TimeSeriesRecord(10, 0), // start bookend
                        TimeSeriesRecord(12, 1),
                        TimeSeriesRecord(18, 2),
                        TimeSeriesRecord(20, 2), // end bookend
                    )
                )
        }
    }

    @Test
    fun `getHistoricTimeSeries omits bookends if not needed`() {
        val docs =
            listOf(
                DummyDoc("0x01", 10, 1, 1),
                DummyDoc("0x02", 15, 2, 2),
                DummyDoc("0x03", 20, 3, 3),
            )
        val result =
            TimeSeriesUtils.getHistoricTimeSeries(
                after = 10L,
                before = 20L,
                findByBlockTimestampBetween = { _, _ -> docs },
                findLatestBeforeOrAtBlockTimestamp = { _ -> null },
                valueExtractor = { it.value },
            )
        expect {
            that(result)
                .isEqualTo(
                    listOf(
                        TimeSeriesRecord(10, 1),
                        TimeSeriesRecord(15, 2),
                        TimeSeriesRecord(20, 3),
                    )
                )
        }
    }
}
