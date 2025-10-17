package org.vechain.indexer.timeseries

data class TimeSeriesRecord<T>(val timestamp: Long, val value: T)
