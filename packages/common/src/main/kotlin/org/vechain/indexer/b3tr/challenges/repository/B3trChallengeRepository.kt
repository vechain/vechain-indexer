package org.vechain.indexer.b3tr.challenges.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.b3tr.challenges.B3trChallenge

@Profile("b3tr-challenges")
@Repository
interface B3trChallengeRepository : BaseIndexedRepository<B3trChallenge, String>
