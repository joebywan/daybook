package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.SeedHash
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Nine daily boards of every puzzle, pinned as the [String.hashCode] of their [ParityFingerprint] line (the line the web
 * page prints under `?dump`; [WebParityDumpTest] writes the JVM's side of a whole year). A board is a pure function of its
 * seed, the `Rng` and any bundled list (Lexicon's words, Mate's positions, Cipher's sums, Word Wheel's lists), so a change to
 * a generator, a ladder it asks to finish the board, or a list's order moves boards and goes red here on purpose.
 * LITS's pins predate making its hash order explicit, so they also pin the Android boards from before that.
 *
 * The web build has no `java.time`, so it reaches the seed through [SeedHash.epochDay] and [SeedHash.daily]; every pinned
 * board is reached both ways.
 */
class BoardPinTest {

    @Test
    fun `epochDay agrees with LocalDate for every day across four centuries`() {
        var date = LocalDate.of(1900, 1, 1)
        val end = LocalDate.of(2300, 12, 31)
        while (!date.isAfter(end)) {
            assertEquals("epochDay($date)", date.toEpochDay(), SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth))
            date = date.plusDays(1)
        }
    }

    @Test
    fun `every puzzle in the registry has pinned boards`() {
        assertEquals(PuzzleRegistry.all.map { it.id }.sorted(), PINS.keys.sorted())
    }

    @Test
    fun `the web route reaches the pinned boards`() {
        for (type in PuzzleRegistry.all) {
            val lines = DATES.flatMap { date ->
                Difficulty.entries.map { tier ->
                    val seed = DailySeed.seedFor(date, type.id, tier)
                    assertEquals(seed, SeedHash.daily(SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth), type.id, tier))
                    ParityFingerprint.line(type, date.toString(), tier, seed)
                }
            }
            assertEquals(lines.joinToString("\n"), PINS[type.id], lines.map { it.hashCode() })
        }
    }

    private companion object {
        val DATES = listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 30), LocalDate.of(2027, 2, 28))

        /** Per puzzle id, the hashes of its 9 lines: each date above, Standard / Hard / Expert. */
        val PINS = mapOf(
            "sudoku" to listOf(321581486, 61942483, -1955678590, 373953106, -1881465627, -547312840, -1666702149, -1406015761, 664600930),
            "kings" to listOf(1126036398, -1173984396, -2027555711, 393486159, -933293852, 502525228, 239534695, -1849362370, 201670159),
            "mambo" to listOf(-74729595, 1340508265, -1592254036, -403225035, 2002546182, -619648626, -963207489, 665564876, 2070831179),
            "pipes" to listOf(2048794146, 1986663670, -1497945378, -426655867, 1298495456, 1657130016, 1286784068, -846558885, 848477196),
            "shikaku" to listOf(1046792915, 448874024, -1512457729, 1276797045, 445733116, 893548544, 732035606, -183110224, -1116164331),
            "mosaic" to listOf(1991709038, -1277690011, 667262966, -565899603, 1834930018, 1099619172, -1372323538, 969628131, -1670583685),
            "sets" to listOf(-1080634186, -1608293369, 614368307, 2039857322, -1069404979, -1213940784, 1152618777, 99657470, 1663685138),
            "atoms" to listOf(1575819939, -52687060, 1467309952, -1141130746, -1827969353, 246921819, 1507404877, -1197903953, -1535227743),
            "snap" to listOf(-1809996519, 1204214709, -1715908999, 1766071738, 685303174, -1092571451, 1395465764, 1565174042, -310630447),
            "lits" to listOf(-206134647, 679286696, 1002471323, 1744480485, -1671960373, 578808405, 355069368, -84760439, 314189877),
            "tower" to listOf(-474855772, -1649481873, 596114753, -815522338, -573593446, 974012700, 1860367855, 851938813, -528490920),
            "words" to listOf(-681355511, 1862217193, 1682635783, 1443840269, -1790280957, 1714700602, -384903312, 1964623706, -1873676658),
            "nonogram" to listOf(1752508638, -1625939962, 2141403329, 19736485, -842812765, -1742655730, 1838771215, 1089667542, 1941448408),
            "inequality" to listOf(1025107459, 1371178428, 1896126469, -52193566, 855727309, 1162442002, -1744467274, 753301278, -1048334304),
            "chess" to listOf(-1373910385, -1996025251, 2080046981, 1331668607, 634446536, -327332564, -387344310, 1925203067, 1542417896),
            "tents" to listOf(940931634, -194075166, 594257890, 1026611820, -699504840, 925276414, -341010275, 1640864579, -931502707),
            "regions" to listOf(1866536695, 1247808727, 545000760, 1728254338, 1052070477, -1050585603, 1730090054, -669292744, -1713518653),
            "cages" to listOf(-1862718606, 143994853, -1779174554, 2059855175, -1291292223, -945129258, 2112846315, -875673327, -250928833),
            "target" to listOf(321820487, 603268927, 1352141401, -370636261, 571995852, -1787017900, -1851755020, -542638594, 2088847740),
            "cipher" to listOf(-79979298, 1671228490, 601322572, -592290008, 778419263, 639822651, -1610681059, 575364404, 783509597),
            "wordwheel" to listOf(1018889751, 1954803247, 1266937928, -936734007, 2126808380, -1562429087, 907427678, -773029108, 1000227698),
            "tally" to listOf(1544727131, -442637926, 1166708192, -1754905174, 1651409005, 599707071, 319744754, -646461482, -392851936),
            "sand" to listOf(-675395325, 1006409573, 632151471, 227227002, 724369054, 1310358744, -559530097, 1118755896, 2040274016),
            "pointers" to listOf(-2069477393, -686690095, -1053062071, -842549642, 616647840, 1960158081, 720094375, -1169757698, -483728013),
            "dominoes" to listOf(-933790730, 734214978, 1124722577, 1993431503, -1221537007, 475649151, 1395522853, 992387815, 1828528781),
        )
    }
}
