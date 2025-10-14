package org.vechain.indexer.constants

// API CONFIG
const val API_ROOT = "/api"
const val API_VERSION = "/v1"
const val API_PATH = "$API_ROOT$API_VERSION"

// API PATHS
const val NFTS_PATH = "$API_PATH/nfts"
const val TRANSACTIONS_PATH = "$API_PATH/transactions"
const val HISTORY_PATH = "$API_PATH/history"
const val TRANSFER_EVENTS_PATH = "$API_PATH/transfers"
const val E2E_PATH = "$API_PATH/e2e"
const val VEVOTE_PATH = "$API_PATH/vevote"
const val STARGATE_PATH = "$API_PATH/stargate"
const val B3TR_PATH = "$API_PATH/b3tr"
const val GM_NFT_PATH = "$B3TR_PATH/galaxy-members"
const val X_ALLOC_PATH = "$B3TR_PATH/xallocations"
const val EXPLORER_PATH = "$API_PATH/explorer"

// PAGINATION DEFAULTS
const val DEFAULT_PAGE_NUMBER = 0
const val DEFAULT_PAGE_SIZE = 20
const val DEFAULT_SORT_DIRECTION = "DESC"
val DEFAULT_SORT_FIELDS = arrayOf("blockNumber", "txId", "_id")
