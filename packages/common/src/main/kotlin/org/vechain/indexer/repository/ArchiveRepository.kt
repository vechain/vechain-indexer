package org.vechain.indexer.repository

import org.springframework.data.repository.CrudRepository
import org.vechain.indexer.model.Archive

interface ArchiveRepository : CrudRepository<Archive<*>, String>