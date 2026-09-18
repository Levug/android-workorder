package com.workorder.app.util

import java.net.URLEncoder

enum class FeedbackKind(val label: String) { BUG("Ошибка"), IDEA("Предложение"), QUESTION("Вопрос") }

object FeedbackMessage {
    fun body(kind: FeedbackKind, description: String, version: String, deviceDetails: String? = null): String =
        buildString {
            append("Тип обращения: ").append(kind.label).append("\n\n")
            append(description.trim()).append("\n\n")
            append("Версия приложения: ").append(version)
            if (!deviceDetails.isNullOrBlank()) append("\n").append(deviceDetails)
        }

    fun issueUrl(kind: FeedbackKind, title: String, body: String): String =
        "https://github.com/Levug/android-workorder/issues/new?title=" +
            encode("[${kind.label}] ${title.trim()}") + "&body=" + encode(body)

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
