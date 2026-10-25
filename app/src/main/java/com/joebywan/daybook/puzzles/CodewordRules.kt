package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng

/**
 * The words a Codeword grid is made of and judged by: Word Wheel's grid lists ([WheelWords.grid], Lexicon's answer
 * screening at every length from 3 to 7), each word also kept as letters (0..25) and as its *shape*, which letters
 * repeat where. A run of numbers can only spell a word of the same shape: equal numbers are equal letters and
 * different numbers different ones.
 */
internal object CodewordWords {

    const val MIN = 3
    const val MAX = 7

    class Length(val words: List<String>) {
        val letters: Array<IntArray> = Array(words.size) { i -> IntArray(words[i].length) { words[i][it] - 'a' } }
        val shapes: IntArray = IntArray(words.size) { shape(letters[it]) }
    }

    private val lengths: List<Length> by lazy { (0..MAX).map { Length(if (it >= MIN) WheelWords.grid(it) else emptyList()) } }

    fun of(length: Int): Length = lengths[length]

    fun isWord(word: String): Boolean = word.length in MIN..MAX && of(word.length).words.binarySearch(word) >= 0

    /**
     * Where values repeat in [v], as one Int: three bits a place, each the index of the first place holding the same
     * value. Two sequences of the same length have the same shape exactly when they repeat in the same places.
     */
    fun shape(v: IntArray): Int {
        var s = 0
        for (i in v.indices) {
            var first = i
            for (j in 0 until i) if (v[j] == v[i]) { first = j; break }
            s = s or (first shl (3 * i))
        }
        return s
    }
}

/**
 * The rules of Codeword, its proof and its generator.
 *
 * A board is a `size x size` grid; `numbers[square]` is 0 for a black square, else the square's number 1..k. A
 * *run* is a maximal line of two or more white squares across or down; every run must spell a word of
 * [CodewordWords], every number stands for one letter and no two numbers for the same one. [isSolved] checks exactly
 * that and never reads a stored answer.
 *
 * Grids come from [CodewordGrids], built offline by `CodewordGridsBuildTest` (a dense fill is a backtracking search
 * per grid, too slow and too variable for a phone). The seed picks an entry and numbers its letters; the device then
 * proves the board again before it ships: the teacher's ladder must finish it from its givens
 * ([CodewordTeacher.solvesByLogic]) and an exhaustive search ([solve]) must find exactly one answer, which becomes
 * the stored solution. `CodewordRulesTest` checks both against `CodewordOracle`, which works letter by letter over
 * the alphabet with no word index at all.
 */
internal object CodewordLogic {

    const val EMPTY = -1

    /** Highlight indices: squares are `0 until size * size`, the key's number n is [key], letter l's key is [letterKey]. */
    const val KEY = 1000
    const val LETTER = 2000

    fun key(number: Int): Int = KEY + number

    fun letterKey(letter: Int): Int = LETTER + letter

    /** Every across run, then every down run, each as its squares in reading order. Runs of one square are not words. */
    fun runs(size: Int, numbers: List<Int>): List<IntArray> {
        val out = mutableListOf<IntArray>()
        for (across in listOf(true, false)) for (line in 0 until size) {
            var i = 0
            while (i < size) {
                fun sq(k: Int) = if (across) line * size + k else k * size + line
                if (numbers[sq(i)] == 0) { i++; continue }
                val start = i
                while (i < size && numbers[sq(i)] != 0) i++
                if (i - start >= 2) out += IntArray(i - start) { sq(start + it) }
            }
        }
        return out
    }

    /** The rules and nothing else: every number a letter, no letter twice, every run a word. */
    fun isSolved(size: Int, numbers: List<Int>, letters: List<Int>): Boolean {
        if (letters.any { it !in 0..25 } || letters.distinct().size != letters.size) return false
        return runs(size, numbers).all { run -> CodewordWords.isWord(String(CharArray(run.size) { 'a' + letters[numbers[run[it]] - 1] })) }
    }

    /** What breaks a rule so far: numbers sharing a letter, and runs that no word can be any more. */
    class Conflicts(val numbers: Set<Int>, val runs: Set<Int>)

    /**
     * A run is dead when no word of its length has its shape, agrees with its placed letters and, where a number is
     * open, uses a letter no number holds. That is a fact about the board, never about the stored answer.
     */
    fun conflicts(size: Int, numbers: List<Int>, letters: List<Int>): Conflicts {
        val twins = (1..letters.size).filter { n -> letters[n - 1] != EMPTY && letters.indices.any { it != n - 1 && letters[it] == letters[n - 1] } }.toSet()
        var held = 0
        for (l in letters) if (l != EMPTY) held = held or (1 shl l)
        val dead = mutableSetOf<Int>()
        runs(size, numbers).forEachIndexed { r, run ->
            val nums = IntArray(run.size) { numbers[run[it]] }
            if (nums.all { letters[it - 1] == EMPTY }) return@forEachIndexed
            val list = CodewordWords.of(run.size)
            val shape = CodewordWords.shape(nums)
            val fits = list.letters.indices.any { w ->
                list.shapes[w] == shape && nums.indices.all { i ->
                    val have = letters[nums[i] - 1]
                    val c = list.letters[w][i]
                    if (have != EMPTY) have == c else held and (1 shl c) == 0
                }
            }
            if (!fits) dead += r
        }
        return Conflicts(twins, dead)
    }

    // ---- proof -------------------------------------------------------------------------------------

    /** What an exhaustive search says. Only [ExactlyOne] carries an answer, so "gave up" cannot pass for one. */
    sealed interface Verdict {
        data object None : Verdict
        class ExactlyOne(val letters: List<Int>) : Verdict
        data object Several : Verdict
    }

    /**
     * Every substitution for the board, counted to two. [known] is a letter per number ([EMPTY] for open). Of the runs
     * not yet spelled, the one the fewest words still fit is branched on; each word assigns the run's open numbers.
     * A spelled run must be a word. No node budget, so it always ends with the true count. [words] is the list a length
     * is judged by; the builder passes a wider one to check that no less common word opens a second answer.
     */
    fun solve(size: Int, numbers: List<Int>, known: List<Int>, words: (Int) -> CodewordWords.Length = CodewordWords::of): Verdict {
        val runs = runs(size, numbers).map { run -> IntArray(run.size) { numbers[run[it]] - 1 } }
        val shapes = runs.map { CodewordWords.shape(it) }
        val assign = known.toIntArray()
        var used = 0
        for (l in assign) if (l != EMPTY) {
            if (used and (1 shl l) != 0) return Verdict.None
            used = used or (1 shl l)
        }
        var found = 0
        var first: List<Int>? = null
        val local = IntArray(assign.size) { EMPTY }

        fun fits(run: IntArray, w: IntArray): Boolean {
            var ok = true
            var taken = 0
            for (i in run.indices) {
                val n = run[i]
                val c = w[i]
                if (assign[n] != EMPTY) {
                    if (assign[n] != c) { ok = false; break }
                } else if (local[n] == EMPTY) {
                    if ((used or taken) and (1 shl c) != 0) { ok = false; break }
                    local[n] = c
                    taken = taken or (1 shl c)
                }
                // A repeat of an open number agrees by shape.
            }
            for (n in run) local[n] = EMPTY
            return ok
        }

        fun search() {
            if (found >= 2) return
            var best = -1
            var bestWords: IntArray? = null
            for (r in runs.indices) {
                val run = runs[r]
                val list = words(run.size)
                if (run.all { assign[it] != EMPTY }) {
                    if (list.words.binarySearch(String(CharArray(run.size) { 'a' + assign[run[it]] }))  < 0) return
                    continue
                }
                val limit = bestWords?.size ?: Int.MAX_VALUE
                val ws = ArrayList<Int>()
                for (w in list.letters.indices) {
                    if (list.shapes[w] != shapes[r] || !fits(run, list.letters[w])) continue
                    ws += w
                    if (ws.size >= limit) break
                }
                if (ws.isEmpty()) return
                if (ws.size < limit) { best = r; bestWords = ws.toIntArray() }
            }
            if (best < 0) {
                found++
                if (first == null) first = assign.toList()
                return
            }
            val run = runs[best]
            val list = words(run.size)
            for (w in bestWords!!) {
                val opened = ArrayList<Int>()
                for (i in run.indices) if (assign[run[i]] == EMPTY) {
                    assign[run[i]] = list.letters[w][i]
                    used = used or (1 shl list.letters[w][i])
                    opened += run[i]
                }
                search()
                for (n in opened) { used = used and (1 shl assign[n]).inv(); assign[n] = EMPTY }
                if (found >= 2) return
            }
        }
        search()
        return when (found) {
            0 -> Verdict.None
            1 -> Verdict.ExactlyOne(first!!)
            else -> Verdict.Several
        }
    }

    // ---- generator ---------------------------------------------------------------------------------

    /** Each attempt draws another entry with `Rng(seed + attempt)`; every entry is proved in tests, so one is enough. */
    const val ATTEMPTS = 8

    /** The grid's side a tier, and the deepest ladder step its boards need ([CodewordTeacher.TECHNIQUES] index). */
    class Spec(val size: Int, val needs: String)

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(7, CodewordTeacher.RUN_AGREES)
        Difficulty.HARD -> Spec(9, CodewordTeacher.CROSSING)
        Difficulty.EXPERT -> Spec(11, CodewordTeacher.ALL_WORDS)
    }

    fun entries(difficulty: Difficulty): List<String> = when (difficulty) {
        Difficulty.STANDARD -> CodewordGrids.STANDARD
        Difficulty.HARD -> CodewordGrids.HARD
        Difficulty.EXPERT -> CodewordGrids.EXPERT
    }

    /** An entry is the grid's squares in reading order (`#` black, else the answer's letter), `:`, the given letters. */
    class Entry(val size: Int, val squares: String, val givens: String) {
        /** The letters the grid uses, in alphabetical order. */
        val alphabet: List<Int> = squares.filter { it != '#' }.map { it - 'a' }.distinct().sorted()

        override fun toString() = "$squares:$givens"
    }

    fun parse(entry: String): Entry {
        val squares = entry.substringBefore(':')
        var size = 0
        while (size * size < squares.length) size++
        require(size * size == squares.length) { "not a square grid: $entry" }
        return Entry(size, squares, entry.substringAfter(':'))
    }

    /**
     * The board for [entry], its letters numbered 1..k in the order [rng] shuffles them to, when the ladder finishes it
     * from its givens and the search proves one answer; else null.
     */
    fun board(entry: Entry, rng: Rng): CodewordState? {
        val order = rng.shuffled(entry.alphabet)
        val numberOf = IntArray(26)
        order.forEachIndexed { i, l -> numberOf[l] = i + 1 }
        val numbers = entry.squares.map { if (it == '#') 0 else numberOf[it - 'a'] }
        val given = entry.givens.map { numberOf[it - 'a'] }.sorted()
        val letters = List(order.size) { if (it + 1 in given) order[it] else EMPTY }
        if (given.isEmpty() || !CodewordTeacher.solvesByLogic(entry.size, numbers, letters)) return null
        val verdict = solve(entry.size, numbers, letters) as? Verdict.ExactlyOne ?: return null
        return CodewordState(size = entry.size, numbers = numbers, solution = verdict.letters, given = given, letters = letters)
    }

    /** Null when nothing was proved. Pure in (seed, difficulty): only the list's own order reaches the `Rng`. */
    fun generateVerified(seed: Long, difficulty: Difficulty): CodewordState? {
        val list = entries(difficulty)
        for (attempt in 0 until ATTEMPTS) {
            val rng = Rng(seed + attempt)
            board(parse(list[rng.nextInt(list.size)]), rng)?.let { return it }
        }
        return null
    }

    /** A fixed board per tier for a seed nothing proved (none ever has: `FallbackTest`); `CodewordRulesTest` proves each. */
    fun lastResort(difficulty: Difficulty): CodewordState = board(parse(entries(difficulty)[0]), Rng(1))!!
}
