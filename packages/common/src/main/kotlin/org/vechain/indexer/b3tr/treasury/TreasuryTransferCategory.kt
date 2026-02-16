package org.vechain.indexer.b3tr.treasury

import com.fasterxml.jackson.annotation.JsonValue

enum class TreasuryTransferCategory {
    EMISSION,
    SURPLUS,
    GM_UPGRADE,
    GRANT,
    GOVERNANCE,
    IN,
    OUT,
    OTHER;

    @JsonValue fun toApiValue(): String = name.lowercase()
}
