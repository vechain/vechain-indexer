package org.vechain.indexer.timeseries

import java.sql.PreparedStatement
import java.sql.ResultSet

/** One series table: its name, its columns beyond [TimeFrameColumns], and its row type. */
interface TimeFrameRowMapping<T : TimeFrameDocument> {
    val table: String

    val columns: List<String>

    /** Binds [columns] in order, starting at parameter [from]. */
    fun bind(ps: PreparedStatement, from: Int, d: T)

    fun read(rs: ResultSet): T
}
