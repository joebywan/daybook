package com.joebywan.daybook.core

/**
 * A message from the developer, shown once over whatever screen the app opens on (`ui/NoticeDialog.kt`).
 *
 * To post one, edit [current] and merge: it ships with the next release (no network: the app has no INTERNET
 * permission). Give each new message a new [Message.id]; a player sees an id once, then never again. Null shows nothing.
 */
object Notice {

    val current: Message? = Message(
        id = "2026-11-stats",
        title = "Thinking about stats",
        body = """
            I'd like to know which puzzles you enjoy and which daily boards are too hard, so I'm thinking about an optional "Send stats" button.

            It would only send when you tap it, and it would show you exactly what it sends first. The app itself would still have no internet access.

            Nothing is decided yet. If you have thoughts, for or against, I'd like to hear them.
        """.trimIndent(),
    )

    const val FEEDBACK_EMAIL = "daybookfeedback@knowhowit.com.au"

    data class Message(val id: String, val title: String, val body: String)

    /** A mailto link with the notice's title as the subject, so replies sort themselves. */
    fun feedbackLink(message: Message): String = "mailto:$FEEDBACK_EMAIL?subject=" + percentEncode("Daybook: ${message.title}")

    /** RFC 3986 percent-encoding of UTF-8 bytes. Hand-rolled: `java.net.URLEncoder` is not on the web (and writes `+`). */
    internal fun percentEncode(text: String): String = buildString {
        for (byte in text.encodeToByteArray()) {
            val b = byte.toInt() and 0xFF
            val c = b.toChar()
            if (b < 0x80 && (c.isLetterOrDigit() || c in "-._~")) append(c)
            else append('%').append(b.toString(16).uppercase().padStart(2, '0'))
        }
    }
}
