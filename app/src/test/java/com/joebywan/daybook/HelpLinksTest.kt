package com.joebywan.daybook

import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.ui.play.GUIDE_URL
import com.joebywan.daybook.ui.play.guideUrl
import com.joebywan.daybook.ui.settings.GITHUB_URL
import com.joebywan.daybook.ui.settings.ISSUES_URL
import com.joebywan.daybook.ui.settings.githubRateLink
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings' Help links (the guide's index, the issues board) and the issue forms they lead to. */
class HelpLinksTest {
    @Test
    fun `the links go where the owner asked`() {
        assertEquals("https://knowhowit.com.au/daybook/guide/", GUIDE_URL)
        assertEquals("https://github.com/joebywan/daybook/issues", ISSUES_URL)
        assertEquals("https://github.com/joebywan/daybook", GITHUB_URL)
    }

    @Test
    fun `every puzzle's guide page sits under the index`() {
        for (p in PuzzleRegistry.all) assertTrue(guideUrl(p.id).startsWith(GUIDE_URL))
    }

    @Test
    fun `the GitHub star row still opens the repository`() {
        var opened: String? = null
        githubRateLink { opened = it }.open()
        assertEquals(GITHUB_URL, opened)
    }

    @Test
    fun `the issue forms ask for the version, the device, the puzzle and a screenshot`() {
        val dir = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, ".github/ISSUE_TEMPLATE") }.first { it.isDirectory }
        val bug = File(dir, "bug_report.yml").readText()
        for (id in listOf("id: version", "id: device", "id: puzzle", "id: tier", "id: screenshot")) {
            assertTrue("bug_report.yml lacks $id", id in bug)
        }
        assertTrue("feature form missing", File(dir, "feature_request.yml").isFile)
        // The form tells players where the version is, so the place must exist under that name.
        assertTrue("Settings" in bug && "Version" in bug)
    }
}
