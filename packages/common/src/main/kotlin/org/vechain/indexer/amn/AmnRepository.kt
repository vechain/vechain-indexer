package org.vechain.indexer.amn

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("authority-nodes")
@Repository
interface AmnRepository : BaseIndexedRepository<AmnEndorser, String> {

    @Query("{ 'endorser': ?0 }") fun findByEndorser(endorser: String): List<AmnEndorser>
}
