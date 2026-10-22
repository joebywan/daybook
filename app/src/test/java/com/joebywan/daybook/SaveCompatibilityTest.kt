package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.data.SavedGame
import com.joebywan.daybook.data.StoredGame
import com.joebywan.daybook.data.savedGameKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Saved games written by builds that reached players, frozen, and read by today's code.
 *
 * A merge to `main` ships to Play, and a save that will not load cannot be fixed on someone's phone. `SavedGame.decode`
 * is lenient on purpose, so a broken save is not a crash but a silently lost game; [StateSerializationTest] cannot see
 * that, because it writes and reads with the same build. These files were written by a released build and are never
 * edited: a failure here means a player's game would be lost or changed. Make the change load the old save instead.
 *
 * Capture a new set into a new file in the PR that adds a puzzle (the coverage test fails until one exists; that PR's
 * build is the one that ships it), and after a deliberate state change ships:
 *
 *     DAYBOOK_SAVE_CAPTURE=src/test/resources/saves/<date>.json \
 *         ./gradlew :app:testDebugUnitTest --tests '*SaveCompatibilityTest*' --rerun
 */
class SaveCompatibilityTest {

    private val json = Json { prettyPrint = true }

    private val captures: List<File> =
        File(checkNotNull(javaClass.getResource("/saves")) { "no saves/ fixtures" }.toURI())
            .listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }

    private fun entries(file: File): List<JsonElement> = Json.parseToJsonElement(file.readText()).jsonArray

    @Test
    fun `every save a released build wrote still loads unchanged`() {
        for (file in captures) for (raw in entries(file)) {
            val label = "${file.name} ${(raw as JsonObject)["key"]}"
            val stored = StoredGame.decode(raw.toString()) ?: throw AssertionError("$label no longer decodes: the player loses this game")
            val puzzleId = stored.key.substringBefore('|')
            val puzzle = PuzzleRegistry.byId(puzzleId) ?: throw AssertionError("$label: no puzzle has id $puzzleId")

            // A field renamed or dropped decodes (unknown keys are ignored) but its value is gone: re-encoding shows it.
            assertEquals("$label: a saved field is no longer read", raw, Json.parseToJsonElement(stored.encode()))

            // The play screen reads these on the way in; a state that decodes but breaks an invariant fails here.
            for (state in stored.game.history + stored.game.state) {
                state.solved
                state.failed
                puzzle.withoutSelection(state)
            }
            puzzle.teach(stored.game.state)
        }
    }

    @Test
    fun `every puzzle has a save captured from a released build`() {
        val captured = captures.flatMap(::entries).map { (it as JsonObject)["key"].toString().trim('"').substringBefore('|') }.toSet()
        val missing = PuzzleRegistry.all.map { it.id }.filterNot { it in captured }
        assertTrue("no released save for $missing: capture a new set in this PR (see the class comment)", missing.isEmpty())
    }

    /** Writes a capture: every puzzle and tier, part-played, with an undo stack and a play log, as `ProgressStore` stores it. */
    @Test
    fun capture() {
        val out = System.getenv("DAYBOOK_SAVE_CAPTURE")
        assumeTrue("set DAYBOOK_SAVE_CAPTURE to write a capture", out != null)
        val played = StateSerializationTest()
        val all = PuzzleRegistry.all.flatMap { puzzle ->
            Difficulty.entries.map { difficulty ->
                val seed = DailySeed.seedFor(LocalDate.of(2026, 10, 22), puzzle.id, difficulty)
                val pristine = puzzle.generate(seed, difficulty)
                val game = SavedGame(played.mutate(pristine), history = listOf(pristine), hints = 1, seconds = 75, log = "hm")
                val stored = StoredGame(savedGameKey(puzzle.id, difficulty, seed), savedAt = 1_792_000_000_000, game = game)
                Json.parseToJsonElement(stored.encode())
            }
        }
        File(out!!).writeText(json.encodeToString(JsonArray.serializer(), JsonArray(all)) + "\n")
    }
}
