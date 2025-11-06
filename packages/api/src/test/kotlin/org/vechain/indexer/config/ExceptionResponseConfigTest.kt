package org.vechain.indexer.config

import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatusCode
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.exception.InternalServerException
import strikt.api.expect
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull

internal class ExceptionResponseConfigTest : ExceptionResponseConfig() {

    private val requestPath = "/api/v1/resources/"
    private val servlet: HttpServletRequest = mockk()

    init {
        every { servlet.requestURI } returns requestPath
        every { servlet.servletPath } returns requestPath
    }

    @Test
    fun `random exception should return internal server error`() {
        val exception = IllegalArgumentException("Some bad argument")

        val res = handleException(servlet, exception)

        expect {
            that(res.statusCode).isEqualTo(HttpStatusCode.valueOf(500))
            that(res.body?.message).isNull()
            that(res.body?.path).isEqualTo(requestPath)
            that(res.body?.status).isEqualTo(500)
            that(res.body?.error).isEqualTo("Internal Server Error")
            that(res.body?.id).isNotNull()
            that(res.body?.timestamp).isNotNull()
        }
    }

    @Test
    fun `bad request exception should return bad request`() {
        val exception = BadRequestException("Invalid address")

        val res = handleHttpException(servlet, exception)

        expect {
            that(res.statusCode).isEqualTo(HttpStatusCode.valueOf(400))
            that(res.body?.message).isEqualTo("Invalid address")
            that(res.body?.path).isEqualTo(requestPath)
            that(res.body?.status).isEqualTo(400)
            that(res.body?.error).isEqualTo("Bad Request")
            that(res.body?.id).isNotNull()
            that(res.body?.timestamp).isNotNull()
        }
    }

    @Test
    fun `500 http exception should NOT include message`() {
        val exception = InternalServerException("Some internal error")

        val res = handleHttpException(servlet, exception)

        expect {
            that(res.statusCode).isEqualTo(HttpStatusCode.valueOf(500))
            that(res.body?.message).isNull()
            that(res.body?.path).isEqualTo(requestPath)
            that(res.body?.status).isEqualTo(500)
            that(res.body?.error).isEqualTo("Internal Server Error")
            that(res.body?.id).isNotNull()
            that(res.body?.timestamp).isNotNull()
        }
    }

    @Test
    fun `handle constraint exception`() {

        val constraint: ConstraintViolation<String> = mockk()
        val constraint2: ConstraintViolation<String> = mockk()
        val constraintException = mockk<ConstraintViolationException>()

        every { constraint.message } returns "Invalid address"
        every { constraint2.message } returns "Invalid address 2"
        every { constraintException.constraintViolations } returns setOf(constraint, constraint2)

        val res = handleConstrainViolation(servlet, constraintException)

        expect {
            that(res.statusCode).isEqualTo(HttpStatusCode.valueOf(400))
            that(res.body?.message).isEqualTo("Invalid address, Invalid address 2")
            that(res.body?.path).isEqualTo(requestPath)
            that(res.body?.status).isEqualTo(400)
            that(res.body?.error).isEqualTo("Bad Request")
            that(res.body?.id).isNotNull()
            that(res.body?.timestamp).isNotNull()
        }
    }
}
// Test comment for workflow validation
