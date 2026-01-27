package org.vechain.indexer.validator

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface ValidatorRepository : PostgresIndexedRepository {
    fun saveAllVersioned(updated: List<Validator>, existing: List<Validator>)

    fun saveAll(validators: List<Validator>)

    fun findAllById(ids: Collection<String>): List<Validator>

    fun findById(id: String): Validator?

    fun findByEndorser(endorser: String, pageable: Pageable): Slice<Validator>

    fun findByStatusNot(status: Status): List<Validator>

    fun findByStatus(status: Status): List<Validator>
}
