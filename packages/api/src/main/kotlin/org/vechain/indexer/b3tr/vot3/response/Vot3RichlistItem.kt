package org.vechain.indexer.b3tr.vot3.response

import java.math.BigInteger
import org.vechain.indexer.b3tr.vot3.Vot3Balance

data class Vot3RichlistItem(val address: String, val balance: BigInteger, val rank: Long) {
    companion object {
        fun from(balance: Vot3Balance, rank: Long): Vot3RichlistItem =
            Vot3RichlistItem(address = balance.address, balance = balance.balance, rank = rank)
    }
}
