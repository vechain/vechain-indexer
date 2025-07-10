package org.vechain.indexer.model

data class TimeSeriesRecord<T>(val timestamp: Long, val value: T)
