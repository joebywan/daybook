package com.joebywan.daybook.data

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LevelBoard
import com.joebywan.daybook.core.Levels
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.Streak
import com.joebywan.daybook.core.praisedTechniquesOf
import com.joebywan.daybook.core.streakOf
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.platform.currentTimeMillis
import com.joebywan.daybook.platform.freshNonce
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.ui.play.ShareCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Lenient on purpose. A board saved by an older build whose state class has since gained or lost a
 * field must degrade to "no saved game", never to a crash on the way into the puzzle.
 */
private val SavedJson = Json { ignoreUnknownKeys = true }

/**
 * One finished puzzle.
 *
 * [day] is null for practice games, which count toward totals but never toward streaks.
 *
 * [playedOn] is the date the solve was made, stored from the build that stopped archive solves counting toward the
 * streak. It is null on every record from before then, and such a record counts as it always did: there is no telling
 * an old archive solve from an old on-the-day one. It is written as a sixth field only when known, so an old record
 * encodes exactly as it was written.
 */
data class Completion(
    val puzzleId: String,
    val difficulty: Difficulty,
    val day: LocalDate?,
    val seconds: Int,
    val hints: Int,
    val playedOn: LocalDate? = null,
) {
    /**
     * The day this solve keeps a streak alive: a daily solved on its own day (or an old record, see [playedOn]), else
     * null. An archive solve still counts for totals, best times and the archive's own marks, never for the streak.
     */
    val streakDay: LocalDate? get() = day?.takeIf { playedOn == null || playedOn == it }

    fun encode(): String =
        (listOf(puzzleId, difficulty.name, day?.toEpochDays()?.toString() ?: "-", seconds, hints) + listOfNotNull(playedOn?.toEpochDays()))
            .joinToString("|")

    companion object {
        /** Reads the old five-field record and the newer one ending in [playedOn]; anything else is null. */
        fun decode(raw: String): Completion? {
            val parts = raw.split("|")
            if (parts.size !in 5..6) return null
            return Completion(
                puzzleId = parts[0],
                difficulty = Difficulty.fromKey(parts[1]),
                day = parts[2].toLongOrNull()?.let { LocalDate.fromEpochDays(it) },
                seconds = parts[3].toIntOrNull() ?: return null,
                hints = parts[4].toIntOrNull() ?: return null,
                playedOn = parts.getOrNull(5)?.let { LocalDate.fromEpochDays(it.toLongOrNull() ?: return null) },
            )
        }
    }
}

/** One board solved in a level, as `level_solves` holds it: `"n|puzzleId|seconds|hints"`. */
data class LevelSolve(val level: Int, val puzzleId: String, val seconds: Int, val hints: Int) {
    fun encode(): String = "$level|$puzzleId|$seconds|$hints"

    companion object {
        fun decode(raw: String): LevelSolve? {
            val parts = raw.split("|")
            if (parts.size != 4) return null
            return LevelSolve(
                level = parts[0].toIntOrNull() ?: return null,
                puzzleId = parts[1],
                seconds = parts[2].toIntOrNull() ?: return null,
                hints = parts[3].toIntOrNull() ?: return null,
            )
        }
    }
}

/**
 * Where the player stands in the levels, derived from the two stored sets and never stored itself.
 *
 * [current] is the lowest opened level that is not done, else the highest done plus one, else 1 (level 1
 * is open from the start). A level is [done] when every puzzle of its plan has a solve; a puzzle no longer
 * in the registry counts as solved, as the spec has it show as finished.
 */
data class LevelProgress(
    val current: Int,
    /** Opened levels and the pool size each was opened with. */
    val opened: Map<Int, Int>,
    val solves: Map<Int, List<LevelSolve>>,
    val done: Set<Int>,
    /**
     * The player's deal (`Levels.LATEST`'s doc): the one their opened levels were stored with, or the latest when
     * nothing is opened yet. One per player, never per level: deck 0 dealt half one way and half the other would
     * repeat some puzzles in levels 1-5 and skip others, so a player who opened a level before deal 2 keeps deal 1.
     */
    val deal: Int = Levels.LATEST,
) {
    /** The pool size [level] plans with: the stored one once opened, the whole pool before. */
    fun poolSize(level: Int): Int = opened[level] ?: Levels.POOL.size

    fun plan(level: Int): List<LevelBoard> = Levels.plan(level, poolSize(level), deal)

    fun solved(level: Int): Set<String> = solves[level].orEmpty().map { it.puzzleId }.toSet()

    /** Playable: done, or the current level. Nothing past [current] can be opened. */
    fun isUnlocked(level: Int): Boolean = level in done || level == current

    companion object {
        val FRESH = of(emptySet(), emptySet())

        /**
         * From the raw `levels` (`"n|poolSize"`, or `"n|poolSize|deal"` since deal 2) and `level_solves` sets;
         * malformed entries are skipped. The deal is the lowest stored, so any entry from before deal 2 means deal 1.
         */
        fun of(levels: Set<String>, levelSolves: Set<String>): LevelProgress {
            val opened = HashMap<Int, Int>()
            var deal: Int? = null
            for (raw in levels) {
                val parts = raw.split("|")
                if (parts.size !in 2..3) continue
                val n = parts[0].toIntOrNull() ?: continue
                val size = parts[1].toIntOrNull()?.takeIf { it in Levels.STARTER..Levels.POOL.size } ?: continue
                val d = (if (parts.size == 2) 1 else parts[2].toIntOrNull())?.takeIf { it in 1..Levels.LATEST } ?: continue
                opened[n] = minOf(size, opened[n] ?: size)
                deal = minOf(d, deal ?: d)
            }
            val playerDeal = deal ?: Levels.LATEST
            val solves = levelSolves.mapNotNull(LevelSolve::decode).filter { it.level >= 1 }.groupBy { it.level }
            fun isDone(n: Int): Boolean {
                val solved = solves[n].orEmpty().map { it.puzzleId }.toSet()
                return Levels.plan(n, opened[n] ?: Levels.POOL.size, playerDeal).all { levelBoardDone(it.puzzleId, solved) }
            }
            val done = (opened.keys + solves.keys).filter { it >= 1 && isDone(it) }.toSet()
            val current = opened.keys.filter { it >= 1 && it !in done }.minOrNull() ?: ((done.maxOrNull() ?: 0) + 1)
            return LevelProgress(current, opened, solves, done, playerDeal)
        }
    }
}

/** Whether a level's board of [puzzleId] counts as done, given the level's [solved] puzzles: one gone from the registry does. */
fun levelBoardDone(puzzleId: String, solved: Set<String>): Boolean = puzzleId in solved || PuzzleRegistry.byId(puzzleId) == null

/**
 * A game left part-finished: the board plus everything the play screen would otherwise lose.
 *
 * The undo stack is part of it because a restored game that cannot be undone is only half restored.
 * Doubles as the payload for the screen's own `rememberSaveable`, so the rotation path and the
 * process-death path can never drift apart in what they preserve.
 */
@Serializable
data class SavedGame(
    val state: PuzzleState,
    val history: List<PuzzleState> = emptyList(),
    val hints: Int = 0,
    val seconds: Int = 0,
    /**
     * The play log (`ui/play/ShareCard.kt`, `PlayLog`): one character per move, undo, restart and hint, which the share
     * graphic is drawn from. Kept whole, unlike [history]. Defaulted: a save from before it loads with an empty log.
     */
    val log: String = "",
    /**
     * Each Restart still in reach of Undo, as the size [history] had once it took the board the restart cleared. An Undo
     * that leaves the history shorter than one has taken that restart back. Defaulted: an older save loads with none.
     */
    val restarts: List<Int> = emptyList(),
    /**
     * The technique (`Deduction.technique`) of every counted hint that opened a deduction, mistakes left out, in order:
     * what the finished frame names (`techniqueLine`). Kept out of [log], whose characters the share card reads.
     * Defaulted: an older save loads with none.
     */
    val taught: List<String> = emptyList(),
) {
    /**
     * Keeps only the tail of the undo stack. Every entry is a whole board, so a Sudoku Expert game
     * a few hundred taps deep would run past half a megabyte — more than Android will carry in a
     * savedInstanceState Bundle, which is a crash rather than a lost undo. The live stack on the
     * play screen stays whole; it is only leaving the screen that costs the oldest steps.
     */
    fun trimmed(): SavedGame {
        val dropped = (history.size - UNDO_DEPTH).coerceAtLeast(0)
        return copy(history = history.drop(dropped), restarts = restarts.map { it - dropped }.filter { it > 0 })
    }

    fun encode(): String = SavedJson.encodeToString(trimmed())

    companion object {
        /** Far deeper than anyone reaches back for, and small enough to be cheap to carry. */
        const val UNDO_DEPTH = 24

        fun decode(raw: String): SavedGame? =
            runCatching { SavedJson.decodeFromString<SavedGame>(raw) }.getOrNull()
    }
}

/** A [SavedGame] tagged with the board it belongs to, as it is held on disk. */
@Serializable
data class StoredGame(val key: String, val savedAt: Long, val game: SavedGame) {
    fun encode(): String = SavedJson.encodeToString(copy(game = game.trimmed()))

    companion object {
        fun decode(raw: String): StoredGame? =
            runCatching { SavedJson.decodeFromString<StoredGame>(raw) }.getOrNull()
    }
}

/** A [StoredGame] read only as far as its key; the game itself is skipped. */
@Serializable
private class StoredKey(val key: String)

/**
 * Identifies one board exactly. A daily puzzle and a practice game of the same genre and
 * difficulty differ by seed, which is what stops yesterday's Sudoku reopening as today's.
 */
fun savedGameKey(puzzleId: String, difficulty: Difficulty, seed: Long): String =
    listOf(puzzleId, difficulty.name, seed).joinToString("|")

/** Which saved games survive a write. Pure, so the eviction rule is testable on its own. */
object SavedGames {

    /**
     * Random games mint a fresh seed every time they are started, so without a ceiling the store
     * would grow a board for every game ever abandoned. One resumable random game per puzzle and tier must fit, and as
     * many again for unfinished dailies, archive days and level boards; derived, so a new puzzle raises it.
     */
    val KEEP = PuzzleRegistry.all.size * Difficulty.entries.size * 2

    fun find(all: List<StoredGame>, key: String): SavedGame? =
        all.firstOrNull { it.key == key }?.game

    fun without(all: List<StoredGame>, key: String): List<StoredGame> =
        all.filterNot { it.key == key }

    /** A solved daily is kept this long so reopening it shows the solve. A day, not a calendar day. */
    const val SOLVED_KEEP_MS = 24L * 60 * 60 * 1000

    /**
     * One entry per board: re-saving replaces, and the stalest games fall off the end. Solved boards
     * (one per tier per puzzle per day, far more than [KEEP]) do not count toward it and expire on their own.
     */
    fun upsert(all: List<StoredGame>, entry: StoredGame): List<StoredGame> {
        val (done, open) = (listOf(entry) + without(all, entry.key))
            .filter { !it.game.state.solved || entry.savedAt - it.savedAt < SOLVED_KEEP_MS }
            .partition { it.game.state.solved }
        return (open.sortedByDescending { it.savedAt }.take(KEEP) + done).sortedByDescending { it.savedAt }
    }
}

/**
 * A share card kept after its finish frame has gone, so the stats screen can share it again. [best] is the player's best
 * solve of the puzzle and tier (any board: daily, level or random, as [Stats.bestTime] counts them all); otherwise it is
 * the last random board solved there. [seconds], [hints], [undos] and [bonus] are the solve's, for ranking ([beats]).
 */
@Serializable
data class KeptCard(
    val puzzleId: String,
    val difficulty: Difficulty,
    val best: Boolean,
    val seconds: Int,
    val hints: Int,
    val card: ShareCard,
    /** Moves taken back, as the share card counts them (`PlayLog.played(log).count { it.undone }`). Defaulted: older cards load. */
    val undos: Int = 0,
    /** Word Wheel's bonus words found; 0 for every other puzzle. Defaulted: older cards load. */
    val bonus: Int = 0,
) {
    /**
     * The owner's order for Best: fewest hints, then fastest, then fewest undos, then most bonus words. Only a strict win
     * beats; a full tie keeps the card already kept.
     */
    fun beats(other: KeptCard): Boolean =
        compareValuesBy(this, other, { it.hints }, { it.seconds }, { it.undos }, { -it.bonus }) < 0

    fun encode(): String = SavedJson.encodeToString(this)

    companion object {
        fun decode(raw: String): KeptCard? = runCatching { SavedJson.decodeFromString<KeptCard>(raw) }.getOrNull()
    }
}

/** Which kept cards survive a write. Pure, so the replace-only-when-beaten rule is testable on its own. */
object KeptCards {
    /** One card per puzzle, tier and kind: a last card always replaces its slot, a best only when it [KeptCard.beats] it. */
    fun keep(all: List<KeptCard>, entry: KeptCard): List<KeptCard> {
        val same = all.firstOrNull { it.puzzleId == entry.puzzleId && it.difficulty == entry.difficulty && it.best == entry.best }
        if (same != null && entry.best && !entry.beats(same)) return all
        return all - setOfNotNull(same) + entry
    }
}

/**
 * All saved progress. Local only — there is no account, no sync and no network permission.
 *
 * [store] is the [KeyValueStore.PROGRESS] file: DataStore on Android, `localStorage` on the web.
 */
class ProgressStore(private val store: KeyValueStore) {

    val completions: Flow<List<Completion>> =
        store.stringSet(KEY_COMPLETIONS).map { raw -> raw.mapNotNull(Completion::decode) }

    suspend fun record(completion: Completion) {
        store.updateStringSet(KEY_COMPLETIONS) { existing ->
            // A given daily puzzle is only ever recorded once; re-solves do not inflate stats.
            val isDuplicate = completion.day != null && existing.any { raw ->
                val other = Completion.decode(raw)
                other?.puzzleId == completion.puzzleId &&
                    other.difficulty == completion.difficulty &&
                    other.day == completion.day
            }
            if (isDuplicate) existing else existing + completion.encode()
        }
    }

    /** Where the player stands in the levels; see [LevelProgress]. */
    val levelProgress: Flow<LevelProgress> =
        combine(store.stringSet(KEY_LEVELS), store.stringSet(KEY_LEVEL_SOLVES), LevelProgress::of)

    /**
     * Records that [level] was opened, with today's pool size and the player's deal unless it was opened before,
     * and returns the plan it must use from now on, so adding a puzzle or a deal never changes a level already
     * started. A first level opened gets `Levels.LATEST`; every later one the deal already stored.
     */
    suspend fun openLevel(level: Int): List<LevelBoard> {
        var plan = emptyList<LevelBoard>()
        store.updateStringSet(KEY_LEVELS) { existing ->
            val p = LevelProgress.of(existing, emptySet())
            val known = level in p.opened
            plan = p.plan(level)
            when {
                known -> existing
                p.deal == 1 -> existing + "$level|${Levels.POOL.size}"
                else -> existing + "$level|${Levels.POOL.size}|${p.deal}"
            }
        }
        return plan
    }

    /**
     * Records a board solved in a level; false (and nothing written) when that puzzle of that level was
     * solved before, so a replay of a finished level records nothing new for it. The caller records the
     * solve as a practice [Completion] (`day = null`) as well, for totals and best times.
     */
    suspend fun recordLevelSolve(solve: LevelSolve): Boolean {
        var added = false
        store.updateStringSet(KEY_LEVEL_SOLVES) { existing ->
            val seen = existing.any { raw ->
                LevelSolve.decode(raw)?.let { it.level == solve.level && it.puzzleId == solve.puzzleId } == true
            }
            added = !seen
            if (seen) existing else existing + solve.encode()
        }
        return added
    }

    /** Every technique a hint has explained, as `puzzleId|technique` ([TechniquesMet]); an older store has none. */
    val techniquesMet: Flow<Set<String>> = store.stringSet(KEY_TECHNIQUES_MET)

    /** Records that a hint on [puzzleId] explained [technique] (`Deduction.technique`). Once each: a set. */
    suspend fun recordTechniqueMet(puzzleId: String, technique: String) {
        store.updateStringSet(KEY_TECHNIQUES_MET) { it + TechniquesMet.encode(puzzleId, technique) }
    }

    /** The game in progress on this board, if one was left behind. */
    suspend fun savedGame(key: String): SavedGame? =
        SavedGames.find(store.stringSet(KEY_SAVED).first().savedGames(), key)

    suspend fun saveGame(key: String, game: SavedGame) {
        store.updateStringSet(KEY_SAVED) { raw ->
            val entry = StoredGame(key, currentTimeMillis(), game)
            SavedGames.upsert(raw.savedGames(), entry).encodeAll()
        }
    }

    /**
     * The random board a puzzle and tier is on. It stays the same until it is solved or the player asks
     * for a new one, which is what lets a random game be resumed. Nonces are only ever read here.
     */
    suspend fun randomNonce(puzzleId: String, difficulty: Difficulty): Long =
        currentRandomNonce(puzzleId, difficulty) ?: newRandomNonce(puzzleId, difficulty)

    /** The random board a puzzle and tier is on, if one has been dealt; never deals one. */
    suspend fun currentRandomNonce(puzzleId: String, difficulty: Difficulty): Long? =
        store.string("random|$puzzleId|${difficulty.name}").first()?.toLongOrNull()

    suspend fun newRandomNonce(puzzleId: String, difficulty: Difficulty): Long =
        freshNonce().also { store.putString("random|$puzzleId|${difficulty.name}", it.toString()) }

    /**
     * Which of [keys] hold a game in progress: saved, unsolved and with a move made, the one kind of unsolved game
     * `PlayScreen` keeps (a restart, or only a selection, clears the save). Only the saves named are decoded in full; the
     * rest are read as far as their key. Re-emits whenever a game is saved, so a board saved on the way out still shows.
     * The most recently saved first (the home screen's "Continue" row is the first).
     */
    fun inProgress(keys: Set<String>): Flow<Set<String>> = store.stringSet(KEY_SAVED).map { raw ->
        raw.filter { r -> runCatching { SavedJson.decodeFromString<StoredKey>(r).key }.getOrNull() in keys }
            .mapNotNull(StoredGame::decode)
            .filter { !it.game.state.solved && it.game.state.moves > 0 }
            .sortedByDescending { it.savedAt }
            .mapTo(mutableSetOf()) { it.key }
    }

    suspend fun clearSavedGame(key: String) {
        store.updateStringSet(KEY_SAVED) { raw ->
            SavedGames.without(raw.savedGames(), key).encodeAll()
        }
    }

    /** The kept share cards ([KeptCard]): an older store has none. */
    val keptCards: Flow<List<KeptCard>> = store.stringSet(KEY_CARDS).map { raw -> raw.mapNotNull(KeptCard::decode) }

    suspend fun keepCard(entry: KeptCard) {
        store.updateStringSet(KEY_CARDS) { raw -> KeptCards.keep(raw.mapNotNull(KeptCard::decode), entry).map(KeptCard::encode).toSet() }
    }

    /**
     * Puzzles that no longer need their walkthrough offered: it was opened (by the player, or by itself on a first
     * board: `autoWalkthrough`), or the puzzle was solved. Builds before the offer line returned recorded it when shown.
     */
    val tutorialsOffered: Flow<Set<String>> = store.stringSet(KEY_TUTORIALS_OFFERED)

    suspend fun markTutorialOffered(puzzleId: String) {
        store.updateStringSet(KEY_TUTORIALS_OFFERED) { offered -> offered + puzzleId }
    }

    /** Whether anything at all is here: a solve, a game left behind, or a walkthrough offered. */
    suspend fun hasPlayed(): Boolean =
        listOf(KEY_COMPLETIONS, KEY_SAVED, KEY_TUTORIALS_OFFERED).any { store.stringSet(it).first().isNotEmpty() }

    private fun Set<String>.savedGames(): List<StoredGame> = mapNotNull(StoredGame::decode)

    private fun List<StoredGame>.encodeAll(): Set<String> = map(StoredGame::encode).toSet()

    private companion object {
        // DataStore keys on Android; they name what is already on every phone, so never rename.
        const val KEY_COMPLETIONS = "completions"
        const val KEY_SAVED = "saved_games"
        const val KEY_TUTORIALS_OFFERED = "tutorials_offered"
        const val KEY_LEVELS = "levels"
        const val KEY_LEVEL_SOLVES = "level_solves"
        const val KEY_CARDS = "share_cards"
        const val KEY_TECHNIQUES_MET = "techniques_met"
    }
}

/**
 * The techniques page (`ui/stats/TechniquesScreen.kt`): per puzzle with a teacher, its praised techniques in
 * `TechniqueNames.kt`'s order, each marked once a hint has explained it. Recorded from the first build that had the
 * page (`techniques_met`); hints opened before it left nothing to read back.
 */
object TechniquesMet {
    fun encode(puzzleId: String, technique: String): String = "$puzzleId|$technique"

    /** [puzzleIds] (the home grid's order) that have a teacher, each with its techniques. Unknown entries are ignored. */
    fun of(raw: Set<String>, puzzleIds: List<String>): List<PuzzleTechniques> =
        puzzleIds.mapNotNull { id ->
            val praised = praisedTechniquesOf(id)
            val names = techniqueNamesOf(id).filterKeys { it in praised }
            if (names.isEmpty()) null
            else PuzzleTechniques(id, names.map { (technique, name) -> MetTechnique(name, encode(id, technique) in raw) })
        }
}

data class MetTechnique(val name: String, val met: Boolean)

data class PuzzleTechniques(val puzzleId: String, val techniques: List<MetTechnique>)

/** Derived numbers for the stats screen. Pure, so it is trivially testable. */
object Stats {

    /** Streak over the days a daily was solved on its own day ([Completion.streakDay]). See [streakOf]. */
    fun streak(all: List<Completion>, today: LocalDate): Streak =
        streakOf(all.mapNotNull { it.streakDay }.toSet(), today)

    /** The time of the best solve in [KeptCard.beats]' order (fewest hints, then fastest), so it matches the Best card. */
    fun bestTime(all: List<Completion>, puzzleId: String, difficulty: Difficulty): Int? =
        all.filter { it.puzzleId == puzzleId && it.difficulty == difficulty }
            .minWithOrNull(compareBy({ it.hints }, { it.seconds }))?.seconds
}

/** The home grid's order (docs/REWARDS.md, "Puzzle order"). Pure, so it is testable on its own. */
object PuzzleOrder {

    /** How far back a daily solve counts toward "most played". */
    const val WINDOW_DAYS = 60

    /**
     * The order a new player meets the puzzles in, gentlest first: the owner's judgement (October 2026) of how quickly a
     * newcomer gets going on Standard. Move a puzzle by moving its id. Sudoku and Lexicon open the second row of a
     * three-column phone grid (places 3 and 4), so the first screen shows something familiar at once (`HomeOrderTest`).
     */
    val GENTLEST: List<String> = listOf(
        "pointers", "pipes", "snap", // See it, do it: one gesture, and the rules are visible on the board.
        "sudoku", "words", "sand", // Familiar, or (Sand) see it, do it.
        "wordwheel", "mini", "target", "nonogram", "mines", // Familiar: most players know them from elsewhere.
        "kings", "mambo", "mosaic", "sets", "inequality", "tents", "lamps", "regions", "tally", // Quick to learn: few rules, instant feedback.
        "shikaku", "atoms", "lits", "chess", "cages", "cipher", "dominoes", "codeword", "pearls", "whoswho", // Takes a bit: more rules, or a longer chain of reasoning.
        "tower", // Last: a colour-code guesser, much the same game as Lexicon, which a new player has met by now.
    )

    /** [ids] in [GENTLEST]'s order; an id it does not name (a puzzle added since) goes last, in [ids]' order. */
    fun gentlest(ids: List<String>): List<String> =
        ids.sortedBy { id -> GENTLEST.indexOf(id).takeIf { it >= 0 } ?: GENTLEST.size }

    /**
     * [ids] with the most solved first: dailies of the last [WINDOW_DAYS] days, plus every practice game (they carry no
     * day). The sort is stable, so ties and puzzles never played keep [ids]' order (the caller passes [gentlest], so a
     * new player's grid is that order). The caller reads [all] once a day rather than live, so a solve never moves a tile
     * under the player's finger on the way back home.
     */
    fun mostPlayed(all: List<Completion>, today: LocalDate, ids: List<String>): List<String> {
        val counts = playCounts(all, today)
        return ids.sortedByDescending { counts[it] ?: 0 }
    }

    private fun playCounts(all: List<Completion>, today: LocalDate): Map<String, Int> {
        val from = today.toEpochDays() - WINDOW_DAYS
        return all.filter { it.day == null || it.day.toEpochDays() >= from }.groupingBy { it.puzzleId }.eachCount()
    }

    /**
     * The player's own order of [ids]: [stored] less any id no longer registered, then every puzzle added since, at the
     * end. So a stored order survives a puzzle being added or removed.
     */
    fun arranged(stored: List<String>, ids: List<String>): List<String> =
        stored.filter { it in ids }.distinct() + ids.filterNot { it in stored }

    /** [order] with the puzzle at [from] taken out and put back at [to]: one drop on the home grid. */
    fun moved(order: List<String>, from: Int, to: Int): List<String> =
        order.toMutableList().apply { add(to, removeAt(from)) }
}
