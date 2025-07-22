package org.vechain.indexer.vevote

import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.repository.VevoteCommentRepository

class VeVoteCommentServiceTest {
    @MockK lateinit var repository: VevoteCommentRepository

    private lateinit var service: VeVoteCommentService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        service =
            VeVoteCommentService(
                repository = repository,
                minLength = 5,
                confidenceThreshold = "0.9",
            )
    }

    @Test
    fun `returns true for English text`() {
        val result = service.isEnglish("This is a comment written in English.")
        Assertions.assertTrue(result)
    }

    @Test
    fun `returns false for Italian text`() {
        val result = service.isEnglish("Questo è un commento scritto in italiano.")
        Assertions.assertFalse(result)
    }

    @Test
    fun `rejects French`() {
        Assertions.assertFalse(service.isEnglish("Ceci est un commentaire écrit en français."))
    }

    @Test
    fun `rejects German`() {
        Assertions.assertFalse(service.isEnglish("Dies ist ein Kommentar auf Deutsch."))
    }

    @Test
    fun `returns false for Russian text`() {
        val result = service.isEnglish("Это комментарий, написанный на русском языке.")
        Assertions.assertFalse(result)
    }

    @Test
    fun `returns false for gibberish`() {
        val result = service.isEnglish("asjkdhaslkdjhaklsdjhasd")
        Assertions.assertFalse(result)
    }

    @Test
    fun `returns false for empty input`() {
        val result = service.isEnglish("")
        Assertions.assertFalse(result)
    }
}
