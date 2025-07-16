package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.model.AuthorityNodeEndorser
import org.vechain.indexer.repository.AuthorityNodeRepository

@Profile("authority-nodes")
@Service
class AuthorityNodeApiEndorserService(
    private val authorityNodeRepository: AuthorityNodeRepository
) {

    // Returns the AuthorityNodeEndorser record for the given user, or null if they are not an
    // endorser.
    fun findByEndorser(user: String): AuthorityNodeEndorser? =
        authorityNodeRepository.findByEndorser(user).firstOrNull()
}
