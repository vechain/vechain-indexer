package org.vechain.indexer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate

class SampleTest : AbstractE2ETest() {

    val restTemplate = RestTemplate()
    val apiUrl = getApiURL()

    @Test
    fun `infrastructure and apps should start`() {
        assertDoesNotThrow {
            println("Test setup correctly. Getting API URL...")
            val res = restTemplate.exchange("${apiUrl}/actuator/health", HttpMethod.GET, null, String::class.java)
            println(res.body)
        }
    }

}