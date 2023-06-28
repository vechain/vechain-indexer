package org.vechain.indexer.exception

import org.springframework.http.HttpStatus

class InternalServerException(message: String?) :
    AbstractHttpException(message, HttpStatus.INTERNAL_SERVER_ERROR)
