package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Pipes
import com.joebywan.daybook.puzzles.PipesState
import com.joebywan.daybook.puzzles.PipesTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The Pipes hint solver, checked against a brute-force enumerator written out again here.
 *
 * The enumerator works on the opposite principle to [PipesTeacher]: no reasoning at all, just every
 * turn of every tile in reading order, pruned only by openings that fail to meet the tile above or
 * to the left, with connectedness checked once a whole board is laid. So the two cannot share a
 * blind spot in how sides propagate.
 *
 * Soundness on generated boards cannot tell reasoning from peeking — they turn out to have exactly
 * one answer, which makes every true fact derivable — so the solver is also run over small boards
 * with *several* answers, where a tile it pins is only sound if every answer agrees with it.
 */
class PipesTeachingTest {

    private fun seeds(count: Int, difficulty: Difficulty, salt: String = "pipes-teach") = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong()), salt, difficulty)
    }

    // ---- independent enumerator -----------------------------------------------------------------

    private fun turnsOf(m: Int): List<Int> {
        val out = mutableListOf<Int>()
        var x = m
        repeat(4) {
            if (x !in out) out += x
            x = ((x shl 1) or (x shr 3)) and 15
        }
        return out
    }

    /** Every answer, up to [cap]: all openings meet, nothing points off the board, one network. */
    private fun allSolutions(w: Int, h: Int, shapes: List<Int>, cap: Int = 5000): List<List<Int>> {
        val n = w * h
        val cur = IntArray(n)
        val out = mutableListOf<List<Int>>()
        fun fits(i: Int, m: Int): Boolean {
            val r = i / w
            val c = i % w
            if (r == 0 && m and 1 != 0) return false
            if (r == h - 1 && m and 4 != 0) return false
            if (c == 0 && m and 8 != 0) return false
            if (c == w - 1 && m and 2 != 0) return false
            if (r > 0 && ((cur[i - w] and 4 != 0) != (m and 1 != 0))) return false
            if (c > 0 && ((cur[i - 1] and 2 != 0) != (m and 8 != 0))) return false
            return true
        }
        fun oneNetwork(): Boolean {
            val seen = BooleanArray(n)
            val stack = ArrayDeque(listOf(0))
            seen[0] = true
            var count = 1
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                val links = listOf(1 to i - w, 2 to i + 1, 4 to i + w, 8 to i - 1)
                for ((bit, j) in links) {
                    if (cur[i] and bit == 0 || seen[j]) continue
                    seen[j] = true
                    count++
                    stack.addLast(j)
                }
            }
            return count == n
        }
        fun walk(i: Int) {
            if (out.size >= cap) return
            if (i == n) {
                if (oneNetwork()) out += cur.toList()
                return
            }
            for (m in turnsOf(shapes[i])) {
                if (!fits(i, m)) continue
                cur[i] = m
                walk(i + 1)
            }
        }
        walk(0)
        return out
    }

    private fun turned(s: PipesState, cell: Int, mask: Int) =
        s.copy(cells = s.cells.toMutableList().also { it[cell] = mask }, moves = s.moves + 1, turned = s.turned + cell)

    // ---- soundness ------------------------------------------------------------------------------

    @Test
    fun `every tile pinned on a generated board holds in every answer it has`() {
        var several = 0
        var boards = 0
        for (difficulty in Difficulty.entries) {
            val count = if (difficulty == Difficulty.EXPERT) 60 else 150
            for (seed in seeds(count, difficulty)) {
                val s = Pipes.generate(seed, difficulty) as PipesState
                val answers = allSolutions(s.width, s.height, s.cells, cap = 50)
                assertTrue("$difficulty/$seed: no answer at all", answers.isNotEmpty())
                boards++
                if (answers.size > 1) several++
                for ((cell, mask) in PipesTeacher.forcedOrder(s.width, s.height, s.cells)) {
                    assertTrue("$difficulty/$seed: tile $cell pinned wrong", answers.all { it[cell] == mask })
                }
                assertTrue("$difficulty/$seed: solve found a non-answer",
                    PipesTeacher.solve(s.width, s.height, s.cells) in answers)
            }
        }
        println("PIPES generated boards with more than one answer: $several of $boards")
    }

    @Test
    fun `on a board with two answers, following the other one is not a mistake`() {
        var checked = 0
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(300, difficulty, "pipes-coverage")) {
                val s = Pipes.generate(seed, difficulty) as PipesState
                val answers = allSolutions(s.width, s.height, s.cells, cap = 50)
                if (answers.size < 2) continue
                val found = PipesTeacher.solve(s.width, s.height, s.cells)!!
                for (other in answers.filter { it != found }) {
                    val differ = other.indices.filter { other[it] != found[it] }.toSet()
                    // The other answer, with one tile both agree on knocked a turn out so it is unsolved.
                    val agreed = other.indices.first { it !in differ && Pipes.rotateCw(other[it]) != other[it] }
                    val cells = other.toMutableList().also { it[agreed] = Pipes.rotateCw(it[agreed]) }
                    val board = s.copy(cells = cells, moves = 20, turned = differ)
                    val d = Pipes.teach(board)!!
                    assertFalse("$difficulty/$seed: flagged a tile of a real answer", d.mistake && d.targets.single() in differ)
                    checked++
                }
            }
        }
        println("PIPES two-answer boards checked for false mistakes: $checked")
        assertTrue("no two-answer board to check", checked > 0)
    }

    /** A random spanning tree by random edge order (Kruskal), not the generator's depth-first walk. */
    private fun randomTree(rng: java.util.Random, w: Int, h: Int): List<Int> {
        val n = w * h
        val parent = IntArray(n) { it }
        fun find(x: Int): Int = if (parent[x] == x) x else find(parent[x]).also { parent[x] = it }
        val edges = buildList {
            for (i in 0 until n) {
                if (i % w < w - 1) add(Triple(i, i + 1, 2))
                if (i / w < h - 1) add(Triple(i, i + w, 4))
            }
        }.shuffled(rng)
        val masks = IntArray(n)
        for ((a, b, bit) in edges) {
            val ra = find(a)
            val rb = find(b)
            if (ra == rb) continue
            parent[ra] = rb
            masks[a] = masks[a] or bit
            masks[b] = masks[b] or (if (bit == 2) 8 else 1)
        }
        return masks.toList()
    }

    @Test
    fun `on boards with several answers, every pinned tile holds in all of them`() {
        val rng = java.util.Random(11)
        var ambiguous = 0
        var pinnedChecked = 0
        repeat(15000) {
            val w = 4 + rng.nextInt(2)
            val h = 4 + rng.nextInt(2)
            val shapes = randomTree(rng, w, h).map { m -> var x = m; repeat(rng.nextInt(4)) { x = Pipes.rotateCw(x) }; x }
            val answers = allSolutions(w, h, shapes)
            if (answers.size < 2) return@repeat
            ambiguous++
            for ((cell, mask) in PipesTeacher.forcedOrder(w, h, shapes)) {
                pinnedChecked++
                assertTrue("tile $cell pinned to $mask but some answer disagrees", answers.all { it[cell] == mask })
            }
            val step = PipesTeacher.deduce(w, h, shapes)
            if (step != null) assertTrue(answers.all { it[step.cell] == step.mask })
        }
        println("PIPES ambiguous boards=$ambiguous pinned tiles checked=$pinnedChecked")
        assertTrue("too few ambiguous boards to mean anything: $ambiguous", ambiguous >= 100)
        assertTrue("no tiles pinned on ambiguous boards: $pinnedChecked", pinnedChecked >= 200)
    }

    // ---- coverage, measured -------------------------------------------------------------------------

    @Test
    fun `hints alone solve every board, and the fallback is rare`() {
        val boards = 300
        val report = StringBuilder("\nPipes coverage ($boards boards per tier, walked from the scramble by hints alone)\n")
        for (difficulty in Difficulty.entries) {
            val counts = PipesTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var fallbackBoards = 0
            for (seed in seeds(boards, difficulty, "pipes-coverage")) {
                var s = Pipes.generate(seed, difficulty) as PipesState
                var sawFallback = false
                var guard = 0
                while (!s.solved) {
                    assertTrue("$difficulty/$seed: walk did not finish", guard++ < 500)
                    val d = Pipes.teach(s)
                    assertNotNull("$difficulty/$seed: no hint on an unsolved board", d)
                    d!!
                    assertFalse("$difficulty/$seed: reached before it was made", d.isReached(s))
                    assertFalse("$difficulty/$seed: a mistake on a board built from hints", d.mistake)
                    counts[d.technique] = counts.getValue(d.technique) + 1
                    if (d.fallback) sawFallback = true
                    val next = d.apply(s) as PipesState
                    assertTrue("$difficulty/$seed: applying it did not reach it", d.isReached(next))
                    assertEquals("$difficulty/$seed: Show me changed more than one tile", 1,
                        next.cells.indices.count { next.cells[it] != s.cells[it] })
                    s = next
                }
                if (sawFallback) fallbackBoards++
            }
            val total = counts.values.sum()
            report.append("  ${difficulty.name.padEnd(8)} steps=$total  ")
            report.append(PipesTeacher.TECHNIQUES.joinToString("  ") {
                "$it ${counts.getValue(it)} (${"%.2f".format(100.0 * counts.getValue(it) / total)}%)"
            })
            report.append("  fallback boards $fallbackBoards/$boards\n")
            assertTrue("$difficulty: fallback on $fallbackBoards of $boards boards", fallbackBoards * 100 < boards)
        }
        println(report)
    }

    // ---- mistakes -----------------------------------------------------------------------------------

    @Test
    fun `a wet tile turned wrong is flagged first, and only once the player has moved`() {
        var flagged = 0
        var explained = 0
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(30, difficulty, "pipes-mistake")) {
                val fresh = Pipes.generate(seed, difficulty) as PipesState
                val answer = allSolutions(fresh.width, fresh.height, fresh.cells, cap = 1)[0]
                val solvedBoard = fresh.copy(cells = answer)
                // Turn one tile off its answer so that it still carries water.
                val candidates = answer.indices.flatMap { i ->
                    generateSequence(Pipes.rotateCw(answer[i])) { Pipes.rotateCw(it) }.take(3)
                        .filter { it != answer[i] }
                        .map { i to it }
                }
                val (cell, wrongMask) = candidates.firstOrNull { (i, m) ->
                    i in Pipes.filled(turned(solvedBoard, i, m))
                } ?: continue
                val broken = turned(solvedBoard, cell, wrongMask)
                assertEquals(setOf(cell), broken.turned)
                val d = Pipes.teach(broken)
                assertNotNull(d)
                d!!
                assertTrue("$difficulty/$seed: wrong wet tile not flagged as a mistake", d.mistake)
                assertEquals("$difficulty/$seed: flagged the wrong tile", setOf(cell), d.targets)
                assertFalse(d.isReached(broken))
                val fixed = d.apply(broken) as PipesState
                assertTrue(d.isReached(fixed))
                assertEquals("$difficulty/$seed: Show me did not put it right", answer[cell], fixed.cells[cell])
                flagged++
                if (d.explanation.contains("only one way round") || d.explanation.contains("can't point off") ||
                    d.explanation.contains("has to") || d.explanation.contains("can only")
                ) {
                    explained++
                }

                // The same tile left that way by the scramble is nobody's mistake.
                assertFalse("$difficulty/$seed: flagged a tile the player never turned",
                    Pipes.teach(broken.copy(turned = emptySet()))!!.mistake)
                // Nor is one that is dry: turning tiles to look is how the game is played.
                val dry = answer.indices.flatMap { i ->
                    generateSequence(Pipes.rotateCw(answer[i])) { Pipes.rotateCw(it) }.take(3)
                        .filter { it != answer[i] }.map { i to it }
                }.firstOrNull { (i, m) -> i !in Pipes.filled(turned(solvedBoard, i, m)) }
                if (dry != null) {
                    assertFalse("$difficulty/$seed: flagged a dry tile",
                        Pipes.teach(turned(solvedBoard, dry.first, dry.second))!!.mistake)
                }
            }
        }
        println("PIPES mistakes flagged=$flagged explained by reasoning=$explained")
        assertTrue("too few mistake cases: $flagged", flagged >= 60)
        assertTrue("mistakes should mostly come with a reason: $explained of $flagged", explained * 2 >= flagged)
    }

    @Test
    fun `wet tiles the scramble left wrong are not mistakes, only turned ones`() {
        val s = Pipes.generate(seeds(1, Difficulty.STANDARD)[0], Difficulty.STANDARD) as PipesState
        val answer = allSolutions(s.width, s.height, s.cells, cap = 1)[0]
        val wet = Pipes.filled(s)
        // The scramble's own wet tiles, untouched, however many moves have been made elsewhere.
        val board = s.copy(moves = 5)
        assertFalse(Pipes.teach(board)!!.mistake)
        // Turn a wet wrong one round a full circle: now the player has turned it, and it is theirs.
        val wetWrong = wet.sorted().firstOrNull { s.cells[it] != answer[it] } ?: return
        var spun = board
        repeat(4) { spun = spun.rotate(wetWrong) }
        val d = Pipes.teach(spun)!!
        assertTrue(d.mistake)
        assertEquals(setOf(wetWrong), d.targets)
    }

    @Test
    fun `a solved board has nothing to teach`() {
        val s = Pipes.generate(seeds(1, Difficulty.HARD)[0], Difficulty.HARD) as PipesState
        val answer = allSolutions(s.width, s.height, s.cells, cap = 1)[0]
        assertNull(Pipes.teach(s.copy(cells = answer, moves = 3)))
    }

    // ---- the walkthrough -------------------------------------------------------------------------

    @Test
    fun `the walkthrough board has exactly one answer, and hints alone reach it`() {
        val answers = allSolutions(3, 3, Pipes.TUTORIAL_SOLUTION)
        assertEquals(listOf(Pipes.TUTORIAL_SOLUTION), answers)
        assertEquals(answers, allSolutions(3, 3, Pipes.TUTORIAL_START))
        assertTrue(PipesState(3, 3, Pipes.TUTORIAL_SOLUTION, 4).solved)
        assertFalse(PipesState(3, 3, Pipes.TUTORIAL_START, 4).solved)
    }

    @Test
    fun `each walkthrough frame takes its one tap, refuses any other, and hands on the board the next expects`() {
        val frames = Pipes.tutorial
        assertTrue(frames.last().freePlay)
        for ((k, frame) in frames.withIndex()) {
            val accepts = frame.accepts ?: continue
            val base = frame.state as PipesState
            val target = frame.highlight.strong.single()
            val intended = base.rotate(target)
            assertTrue("frame $k: refused the intended tap", accepts(intended))
            assertEquals("frame $k: the intended tap does not finish the tile",
                Pipes.TUTORIAL_SOLUTION[target], intended.cells[target])
            for (other in base.cells.indices) {
                if (other == target) continue
                val stray = base.rotate(other)
                if (stray.cells == base.cells) continue
                assertFalse("frame $k: accepted a tap on tile $other", accepts(stray))
            }
            assertFalse("frame $k: accepted two taps", accepts(intended.rotate(target)))
            assertEquals("frame $k: next frame starts from a different board",
                intended.cells, (frames[k + 1].state as PipesState).cells)
        }
    }

    @Test
    fun `your turn is finished by hints with no mistake and no fallback`() {
        var s = Pipes.tutorial.last().state as PipesState
        assertFalse(s.solved)
        var guard = 0
        while (!s.solved) {
            assertTrue(guard++ < 20)
            val d = Pipes.teach(s)!!
            assertFalse(d.mistake)
            assertFalse(d.fallback)
            assertNotEquals("", d.explanation)
            s = d.apply(s) as PipesState
        }
    }

    /**
     * The hint panel shows about four lines of body text on a phone, roughly 200 characters, and a
     * nudge is one short line. Every nudge and explanation the teacher produces is checked, on two years
     * of daily boards per tier (730) walked by hints alone, with a wet tile turned the wrong way planted on each
     * board as it stands (the mistake wording), and on the walkthrough. The limits are written out
     * here, not read from the teacher.
     */
    @Test
    fun `every nudge and explanation fits the panel`() {
        val nudgeLimit = 70
        val explanationLimit = 200
        var checked = 0
        var mistakes = 0
        var longest = ""
        // Every overflow is collected, not just the first, so one run shows the whole job.
        val over = sortedSetOf<String>()
        fun check(label: String, d: com.joebywan.daybook.core.Deduction) {
            checked++
            if (d.explanation.length > longest.length) longest = d.explanation
            if (d.nudge.length > nudgeLimit) over += "nudge ${d.nudge.length}: ${d.nudge}"
            if (d.explanation.length > explanationLimit) over += "explanation ${d.explanation.length}: ${d.explanation}"
        }
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(730, difficulty, "pipes-length")) {
                var s = Pipes.generate(seed, difficulty) as PipesState
                // Walk once to learn the answer the hints lead to, keeping the boards on the way.
                val path = mutableListOf<PipesState>()
                var guard = 0
                while (!s.solved) {
                    assertTrue("$difficulty/$seed: walk did not finish", guard++ < 500)
                    path += s
                    val d = Pipes.teach(s)!!
                    check("$difficulty/$seed ${d.technique}", d)
                    s = d.apply(s) as PipesState
                }
                val answer = s.cells
                // A few boards along the way, each with a wet tile turned off the answer.
                for (k in path.indices step maxOf(1, path.size / 4)) {
                    val board = path[k]
                    val wet = Pipes.filled(board)
                    val tiles = board.cells.indices.filter { it in wet && board.cells[it] == answer[it] }
                    for (cell in tiles.filterIndexed { j, _ -> j % maxOf(1, tiles.size / 3) == 0 }) {
                        val wrong = generateSequence(Pipes.rotateCw(answer[cell])) { Pipes.rotateCw(it) }.take(3)
                            .firstOrNull { it != answer[cell] && cell in Pipes.filled(turned(board, cell, it)) } ?: continue
                        val m = Pipes.teach(turned(board, cell, wrong))!!
                        if (!m.mistake) continue
                        mistakes++
                        check("$difficulty/$seed wrong tile", m)
                    }
                }
            }
        }
        for (frame in Pipes.tutorial) {
            assertTrue("walkthrough caption is ${frame.caption.length} characters", frame.caption.length <= 170)
        }
        assertTrue("${over.size} texts do not fit the panel:\n" + over.joinToString("\n"), over.isEmpty())
        println("pipes text fit: $checked hints checked ($mistakes mistakes), longest explanation ${longest.length}: $longest")
        assertTrue("barely checked anything: $checked", checked > 10_000)
        assertTrue("too few mistakes planted: $mistakes", mistakes > 1000)
    }

    @Test
    fun `explanations read as sentences and cite only real neighbours`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(40, difficulty, "pipes-words")) {
                var s = Pipes.generate(seed, difficulty) as PipesState
                while (!s.solved) {
                    val d = Pipes.teach(s)!!
                    val cell = d.targets.single()
                    assertTrue(d.explanation.endsWith("."))
                    assertTrue(d.explanation.first().isUpperCase())
                    // A loop cites the path the long way round; everything else cites neighbours.
                    if (d.technique == PipesTeacher.NO_LOOP) {
                        assertTrue(d.explanation.contains("loop"))
                        s = d.apply(s) as PipesState
                        continue
                    }
                    for (c in d.cited) {
                        val adjacent = (c / s.width == cell / s.width && kotlin.math.abs(c - cell) == 1) ||
                            kotlin.math.abs(c - cell) == s.width
                        assertTrue("$difficulty/$seed: cited $c is not beside $cell", adjacent)
                    }
                    s = d.apply(s) as PipesState
                }
            }
        }
    }
}
