package com.workorder.app.util

import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class FeedbackMessageTest {
    @Test fun cyrillicNewlinesAndUrlCharactersSurviveGitHubLink() {
        val title = "Ошибка & график? #1 + 2"
        val description = "Первая строка\nВторая & строка #3"
        val body = FeedbackMessage.body(FeedbackKind.BUG, description, "3.5-beta")
        val url = URI(FeedbackMessage.issueUrl(FeedbackKind.BUG, title, body))
        assertEquals("github.com", url.host)
        assertEquals("/Levug/android-workorder/issues/new", url.path)
        val params = url.rawQuery.split("&").associate {
            val parts = it.split("=", limit = 2)
            parts[0] to URLDecoder.decode(parts[1], "UTF-8")
        }
        assertEquals("[Ошибка] $title", params["title"])
        assertEquals(body, params["body"])
        assertTrue(body.contains(description))
        assertFalse(body.contains("Android"))
        assertTrue(FeedbackMessage.body(FeedbackKind.IDEA, "Текст", "3.5", "Android test").contains("Android test"))
    }
}
