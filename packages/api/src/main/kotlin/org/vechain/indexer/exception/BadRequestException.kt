package org.vechain.indexer.exception

import org.springframework.http.HttpStatus

class BadRequestException(message: String) : AbstractHttpException(message, HttpStatus.BAD_REQUEST)
