package org.vechain.indexer.vevote

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class VeVoteCommentServiceTest {
    private val service = VeVoteCommentService(minLength = 5, confidenceThreshold = "0.9")

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
