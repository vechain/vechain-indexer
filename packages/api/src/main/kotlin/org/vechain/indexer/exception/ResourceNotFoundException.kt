package org.vechain.indexer.exception

import org.springframework.http.HttpStatus

class ResourceNotFoundException(message: String) :
  AbstractHttpException(message, HttpStatus.NOT_FOUND)
