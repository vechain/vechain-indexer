package org.vechain.indexer.b3tr.richlist.response

import java.math.BigInteger

data class B3trRichlistItem(val address: String, val balance: BigInteger, val rank: Long)
