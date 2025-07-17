package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.AmnEndorser

@Profile("authority-nodes")
@Repository
interface AmnRepository : BaseIndexedRepository<AmnEndorser, String> {

    fun findByEndorser(endorser: String): List<AmnEndorser>
}
