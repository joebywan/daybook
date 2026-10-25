package com.joebywan.daybook

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Play refuses a "What's new" over 500 characters, and `publish-play.yml` only finds out after the merge,
 * once the release is built. Catch it on the pull request instead.
 */
class ChangelogLengthTest {

    private val path = "fastlane/metadata/android/en-US/changelogs/default.txt"

    @Test
    fun `whats new fits Play's 500 characters`() {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, path).exists()) d = d.parentFile
        val text = File(d ?: error("$path not found"), path).readText().trim()
        val n = text.codePointCount(0, text.length)
        assertTrue("$path is $n characters; Play allows 500", n <= 500)
    }
}
