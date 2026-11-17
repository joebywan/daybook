package com.joebywan.daybook

import com.joebywan.daybook.core.Notice
import org.junit.Assert.assertEquals
import org.junit.Test

class NoticeTest {

    /** Against the JDK's encoder, which writes '+' for a space and leaves '*' alone where RFC 3986 encodes it. */
    @Test
    fun percentEncodingMatchesTheJdk() {
        val text = "Daybook: thoughts & \"stats\" — ça va? 100%/*~"
        val jdk = java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~")
        assertEquals(jdk, Notice.percentEncode(text))
    }

    @Test
    fun feedbackLinkCarriesTheTitle() {
        val link = Notice.feedbackLink(Notice.Message("x", "Hi there", ""))
        assertEquals("mailto:daybookfeedback@knowhowit.com.au?subject=Daybook%3A%20Hi%20there", link)
    }
}
