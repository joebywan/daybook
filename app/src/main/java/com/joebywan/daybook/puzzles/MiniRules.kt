package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict

/**
 * The rules of the Mini crossword, its proof and its generator.
 *
 * A board is a `size x size` grid, a string in reading order: [BLACK] for a black square, else a letter (`a..z`), or
 * [OPEN] on the player's side for a square not filled yet. A *slot* is a maximal line of two or more white squares
 * across or down; every slot has a clue, and is right when it spells the word of the list whose clue that is. Clues are
 * distinct across Word Wheel's and Lexicon's lists (`LexiconCluesTest`, `WordWheelRulesTest`), so a clue names one word:
 * [isSolved] checks exactly that, never a stored answer.
 *
 * Grids come from [MiniGrids], built offline by `MiniGridsBuildTest` from Word Wheel's grid lists ([WheelWords.grid],
 * Lexicon's answer screening) less a breakfast-table list; the device proves each board again before it ships
 * ([board]).
 */
internal object MiniLogic {

    const val BLACK = '#'
    const val OPEN = ' '

    /** Highlight indices: squares are `0 until size * size`, letter l's key is [letterKey], the clue bar [CLUE_BAR]. */
    const val LETTER = 2000
    const val CLUE_BAR = 3000

    fun letterKey(letter: Int): Int = LETTER + letter

    /** A slot: its clue [number], which way it runs, and its squares in order. */
    class Slot(val number: Int, val across: Boolean, val squares: IntArray) {
        val length get() = squares.size
        val name get() = "$number ${if (across) "Across" else "Down"}"
    }

    /**
     * Every across slot, then every down slot, each in reading order of its first square, numbered as a printed
     * crossword is: a square starting a slot either way takes the next number, in reading order.
     */
    fun slots(size: Int, grid: String): List<Slot> {
        fun white(r: Int, c: Int) = r in 0 until size && c in 0 until size && grid[r * size + c] != BLACK
        val numberAt = IntArray(size * size)
        var next = 1
        for (r in 0 until size) for (c in 0 until size) {
            if (!white(r, c)) continue
            val across = !white(r, c - 1) && white(r, c + 1)
            val down = !white(r - 1, c) && white(r + 1, c)
            if (across || down) numberAt[r * size + c] = next++
        }
        val out = mutableListOf<Slot>()
        for (across in listOf(true, false)) for (r in 0 until size) for (c in 0 until size) {
            val n = numberAt[r * size + c]
            if (n == 0) continue
            val (dr, dc) = if (across) 0 to 1 else 1 to 0
            if (white(r - dr, c - dc) || !white(r + dr, c + dc)) continue
            var k = 0
            while (white(r + dr * k, c + dc * k)) k++
            out += Slot(n, across, IntArray(k) { (r + dr * it) * size + c + dc * it })
        }
        return out
    }

    /** The clue numbers by square, 0 for none: what the grid prints in its corners. */
    fun numbers(size: Int, grid: String): IntArray {
        val out = IntArray(size * size)
        for (s in slots(size, grid)) out[s.squares[0]] = s.number
        return out
    }

    fun spelled(grid: String, slot: Slot): String = CharArray(slot.length) { grid[slot.squares[it]] }.concatToString()

    /** The clue [word] has in the lists, or null for a word they do not hold. */
    fun clueOf(word: String): String? = if (WheelWords.isGridWord(word)) WheelWords.clue(word) else null

    // Probed by word, never iterated, so its order cannot reach a seed.
    private val secondClues: Map<String, String> by lazy {
        MiniSecondClueText.ALL.split("\n").associate { it.substringBefore(": ") to it.substringAfter(": ") }
    }

    /** [word]'s second clue (`tools/words/second-clues.txt`), a different angle from its first, or "" for none. */
    fun secondClueOf(word: String): String = secondClues[word] ?: ""

    /** The rules and nothing else: every slot spells the word its clue is the clue of. [clues] is one per slot, in slot order. */
    fun isSolved(size: Int, letters: String, clues: List<String>): Boolean {
        val slots = slots(size, letters)
        return slots.size == clues.size && slots.indices.all { clueOf(spelled(letters, slots[it])) == clues[it] }
    }

    /**
     * The slots that break a rule so far: filled in and spelling no word of the list. A fact about the letters alone,
     * never about the stored answer (a real word under another clue is not flagged: that would be reading the answer).
     */
    fun deadSlots(size: Int, letters: String): Set<Int> {
        val slots = slots(size, letters)
        return slots.indices.filter { r ->
            val w = spelled(letters, slots[r])
            OPEN !in w && !WheelWords.isGridWord(w)
        }.toSet()
    }

    /** The slots shown red: [deadSlots], but only once no square is open, so the red never checks a word mid-grid. */
    fun shownDead(size: Int, letters: String): Set<Int> = if (OPEN in letters) emptySet() else deadSlots(size, letters)

    // ---- proof -------------------------------------------------------------------------------------


    /**
     * Every fill of [letters]' open squares in which every slot spells a word of the grid lists, counted to two. The open
     * slot the fewest words fit is branched on. No node budget, so it always ends with the true count. Clues play no part:
     * this proves that the letters given and the crossings alone leave one grid.
     */
    fun solve(size: Int, letters: String): Verdict<String> {
        val slots = slots(size, letters)
        val grid = letters.toCharArray()
        var found = 0
        var first: String? = null
        fun fits(slot: Slot, w: String) = slot.squares.indices.all { grid[slot.squares[it]] == OPEN || grid[slot.squares[it]] == w[it] }
        fun search() {
            if (found >= 2) return
            var best: Slot? = null
            var bestWords: List<String>? = null
            for (slot in slots) {
                val now = CharArray(slot.length) { grid[slot.squares[it]] }.concatToString()
                if (OPEN !in now) {
                    if (!WheelWords.isGridWord(now)) return
                    continue
                }
                val ws = WheelWords.grid(slot.length).filter { fits(slot, it) }
                if (ws.isEmpty()) return
                if (bestWords == null || ws.size < bestWords.size) { best = slot; bestWords = ws }
            }
            if (best == null) {
                found++
                if (first == null) first = grid.concatToString()
                return
            }
            for (w in bestWords!!) {
                val opened = best.squares.filter { grid[it] == OPEN }
                best.squares.forEachIndexed { i, sq -> grid[sq] = w[i] }
                search()
                for (sq in opened) grid[sq] = OPEN
                if (found >= 2) return
            }
        }
        search()
        return Verdict.of(found) { first!! }
    }

    // ---- generator ---------------------------------------------------------------------------------

    /** Each attempt draws another entry with `Rng(seed + attempt)`; every entry is proved in tests, so one is enough. */
    const val ATTEMPTS = 8

    /**
     * The grid's side a tier, and the band its *clue share* must fall in: the letters the hints' walk reads from the
     * clues ([MiniTeacher.CLUE]) as a share of the white squares, more than [abovePercent] and at most [upToPercent].
     * The rest the crossings settle. A higher share leaves more of the grid to the clues alone.
     */
    class Spec(val size: Int, val abovePercent: Int, val upToPercent: Int) {
        fun accepts(clueLetters: Int, whites: Int): Boolean = clueLetters * 100 > abovePercent * whites && clueLetters * 100 <= upToPercent * whites
    }

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(5, 0, 30)
        Difficulty.HARD -> Spec(6, 30, 40)
        Difficulty.EXPERT -> Spec(7, 40, 100)
    }

    fun entries(difficulty: Difficulty): List<String> = when (difficulty) {
        Difficulty.STANDARD -> MiniGrids.STANDARD
        Difficulty.HARD -> MiniGrids.HARD
        Difficulty.EXPERT -> MiniGrids.EXPERT
    }

    fun sizeOf(entry: String): Int {
        var size = 0
        while (size * size < entry.length) size++
        require(size * size == entry.length) { "not a square grid: $entry" }
        return size
    }

    /**
     * The board for [entry] (a filled grid in reading order) when every slot spells a grid word, none twice, the hints'
     * walk finishes it needing exactly the tier's deepest step ([MiniTeacher.walk]), and the search proves that the letters
     * the walk read from the clues and the crossings leave one grid; else null.
     */
    fun board(entry: String, difficulty: Difficulty): MiniState? {
        val size = sizeOf(entry)
        val slots = slots(size, entry)
        val words = slots.map { spelled(entry, it) }
        if (words.distinct().size != words.size) return null
        val clues = words.map { clueOf(it) ?: return null }
        val walk = MiniTeacher.walk(size, entry) ?: return null
        val clueLetters = walk.count { it.technique == MiniTeacher.CLUE }
        if (size != specFor(difficulty).size || !specFor(difficulty).accepts(clueLetters, entry.count { it != BLACK })) return null
        val read = CharArray(size * size) { if (entry[it] == BLACK) BLACK else OPEN }
        for (hit in walk) if (hit.technique == MiniTeacher.CLUE) read[hit.square] = hit.letter
        val proof = solve(size, read.concatToString()) as? Verdict.ExactlyOne ?: return null
        if (proof.value != entry) return null
        return MiniState(size = size, answer = entry, clues = clues, secondClues = words.map(::secondClueOf), letters = read.map { if (it == BLACK) BLACK else OPEN }.joinToString(""))
    }

    fun generate(seed: Long, difficulty: Difficulty): MiniState = generateVerified(seed, difficulty) ?: lastResort(difficulty)

    /** The board when it was proved, null otherwise (what `FallbackTest` asserts on). Pure in (seed, difficulty): only the list's own order reaches the `Rng`. */
    fun generateVerified(seed: Long, difficulty: Difficulty): MiniState? {
        val list = entries(difficulty)
        for (attempt in 0 until ATTEMPTS) {
            val rng = Rng(seed + attempt)
            board(list[rng.nextInt(list.size)], difficulty)?.let { return it }
        }
        return null
    }

    /** A fixed board per tier for a seed nothing proved (none ever has: `FallbackTest`); `MiniRulesTest` proves each. */
    fun lastResort(difficulty: Difficulty): MiniState = board(entries(difficulty)[0], difficulty)!!
}
