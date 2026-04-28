package org.vechain.indexer.b3tr.challenges

/**
 * Semantic filter for `GET /api/v1/b3tr/users/{wallet}/challenges`. Each value combines a challenge
 * status set with the caller's relationship to the challenge, so a UI tab can map to a single
 * filter value without composing lower-level status / role params.
 */
enum class ChallengeFilter {
    /**
     * Challenges requiring a wallet-level action: outstanding invites, claimable prizes on a
     * Completed MaxActions challenge the wallet won, finalizable MaxActions challenges past their
     * endRound, or reclaimable stake on Cancelled / Invalid challenges.
     */
    NeededAction,

    /** Pending or Active challenges the wallet either created or has joined. */
    MyChallenges,

    /** Public Pending challenges the wallet is not yet involved in. */
    OpenToJoin,

    /** Public Active challenges the wallet is not involved in. */
    OthersActive,

    /**
     * Challenges the wallet considers "no longer current":
     * - Terminal-state (Completed, Cancelled, Invalid) challenges the wallet has been involved in.
     * - Pending or Active challenges the wallet has actively bowed out of: declined invitations and
     *   joined-then-left participants. Surfacing these here lets the wallet re-accept / re-join
     *   from the History view without losing the challenge from sight.
     */
    History,
}
