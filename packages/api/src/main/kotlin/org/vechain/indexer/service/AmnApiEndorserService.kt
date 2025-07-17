package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.model.AmnEndorser
import org.vechain.indexer.repository.AmnRepository

@Profile("authority-nodes")
@Service
class AmnApiEndorserService(private val amnRepository: AmnRepository) {

    // Returns the AmnEndorser record for the given user, or null if they are not an
    // endorser.
    fun findByEndorser(user: String): AmnEndorser? =
        amnRepository.findByEndorser(user).firstOrNull()
}
