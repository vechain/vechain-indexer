package org.vechain.indexer.vevote

import org.vechain.indexer.postgres.PostgresIndexedRepository

interface HistoricProposalsVoteRepository : PostgresIndexedRepository {
    fun saveAll(votes: List<HistoricProposalsVote>)

    fun getCollectionName(): String
}
