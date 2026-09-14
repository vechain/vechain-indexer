package org.vechain.indexer.stargate.timeFrame

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort.Direction

/** Offset paging as the Mongo repositories did it: one row past the page decides hasNext. */
fun <T> timeFrameSlice(
    pageable: Pageable,
    fetch: (direction: Direction, offset: Long, limit: Int) -> List<T>,
): Slice<T> {
    val direction = pageable.sort.firstOrNull()?.direction ?: Direction.ASC
    val rows = fetch(direction, pageable.offset, pageable.pageSize + 1)
    return SliceImpl(rows.take(pageable.pageSize), pageable, rows.size > pageable.pageSize)
}
