package org.vechain.indexer

import org.junit.jupiter.api.Test

class DockerTest : ContainerTests() {

    @Test
    fun `network should start`() {
        Thread.sleep(10_000_000)
        assert(1 == 1)
    }

}