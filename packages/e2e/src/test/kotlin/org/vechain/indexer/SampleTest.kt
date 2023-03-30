package org.vechain.indexer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class SampleTest : AbstractE2ETest() {

    @Test
    fun `infrastructure and apps should start`() {
        assertDoesNotThrow {
            val apiURL = getApiURL()
            println(apiURL)
        }
    }

}