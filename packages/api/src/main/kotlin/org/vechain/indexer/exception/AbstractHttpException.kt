package org.vechain.indexer.exception

import org.springframework.http.HttpStatus

abstract class AbstractHttpException(override val message: String?, val status: HttpStatus) :
  Exception(message)
