package org.vechain.indexer.safe

/**
 * Filter applied to `getSafesForOwner`. `ALL` returns both current and past memberships, `CURRENT`
 * filters where `removedBlock == null`, `PAST` filters where `removedBlock != null`.
 */
enum class SafeMembershipScope {
    ALL,
    CURRENT,
    PAST,
}
