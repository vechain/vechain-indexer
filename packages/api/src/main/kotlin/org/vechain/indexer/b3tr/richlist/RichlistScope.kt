package org.vechain.indexer.b3tr.richlist

import java.math.BigDecimal
import org.vechain.indexer.b3tr.balance.B3trBalance
import org.vechain.indexer.b3tr.balance.B3trBalanceColumn

enum class RichlistScope {
    /** Combined VOT3 + B3TR balance and rank. */
    ALL,

    /** B3TR balance only (excludes B3TR held by VOT3 contract). */
    B3TR,

    /** VOT3 balance only. */
    VOT3;

    val sortField: String
        get() =
            when (this) {
                ALL -> "totalBalance"
                VOT3 -> "vot3Balance"
                B3TR -> "b3trBalance"
            }

    val column: B3trBalanceColumn
        get() =
            when (this) {
                ALL -> B3trBalanceColumn.TOTAL
                VOT3 -> B3trBalanceColumn.VOT3
                B3TR -> B3trBalanceColumn.B3TR
            }

    fun balanceFor(doc: B3trBalance): BigDecimal =
        when (this) {
            ALL -> doc.totalBalance
            VOT3 -> doc.vot3Balance
            B3TR -> doc.b3trBalance
        }
}
