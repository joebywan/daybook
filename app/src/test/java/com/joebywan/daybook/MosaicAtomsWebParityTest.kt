package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Atoms
import com.joebywan.daybook.puzzles.AtomsState
import com.joebywan.daybook.puzzles.Mosaic
import com.joebywan.daybook.puzzles.MosaicState
import com.joebywan.daybook.puzzles.PuzzleState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Mosaic and Atoms on the web route, pinned the same way [WebParityTest] pins Kings.
 *
 * The web page prints these lines, in this format, under `?dump`; [WebParityDumpTest] writes the
 * JVM's side of a whole year for diffing against the browser — and against an earlier commit.
 *
 * The two Atoms Expert lines for 2026-01-01 and 2027-02-28 were re-pinned when this met Atoms'
 * teaching work on main, which changed those boards on Android as well; they are main's boards.
 */
class MosaicAtomsWebParityTest {

    @Test
    fun `the web route reaches the same Mosaic and Atoms boards as the app`() {
        val lines = listOf(Mosaic, Atoms).flatMap { type ->
            PARITY_DATES.flatMap { date ->
                Difficulty.entries.map { tier ->
                    val seed = DailySeed.seedFor(date, type.id, tier)
                    val epochDay = SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth)
                    assertEquals(seed, SeedHash.daily(epochDay, type.id, tier))
                    "${type.id} ${fingerprint(date.toString(), tier, seed, type.generate(seed, tier))}"
                }
            }
        }
        lines.forEach(::println)
        assertEquals(FINGERPRINTS, lines)
    }

    private fun fingerprint(date: String, tier: Difficulty, seed: Long, state: PuzzleState): String =
        "$date ${tier.name} seed=$seed " + when (state) {
            is MosaicState ->
                "${state.width}x${state.height} colours=${state.colours} limit=${state.limit} " +
                    "cells=${state.cells.joinToString("")}"
            is AtomsState ->
                "n=${state.size} atoms=${state.atoms.joinToString(";") { "${it.row},${it.col},${it.bonds}" }} " +
                    "pairs=${state.pairs.joinToString(";") { "${it.a}-${it.b}${if (it.horizontal) "h" else "v"}" }} " +
                    "solution=${state.solution.joinToString("")}"
            else -> error("not a Mosaic or Atoms board")
        }

    private companion object {
        val PARITY_DATES = listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 9, 30),
            LocalDate.of(2027, 2, 28),
        )

        val FINGERPRINTS = listOf(
            "mosaic 2026-01-01 STANDARD seed=-6380546271103897721 8x12 colours=3 limit=4 cells=222222002222222200221222000212221100102110000021202221112222210002222212010001120100001101001111",
            "mosaic 2026-01-01 HARD seed=-5457262211716178932 9x14 colours=4 limit=5 cells=100000332112222332222220002222210001002112011001112233001112233222100333022000000002300222003330223000331233331111133331111122",
            "mosaic 2026-01-01 EXPERT seed=1073208080966987541 10x16 colours=5 limit=6 cells=2044411111244444411121444011111113000331113300003311001000013320100111322224443332223444334403344433400333233340033324434403333444133332244422333424442222442444",
            "mosaic 2026-09-30 STANDARD seed=2463883845117716255 8x12 colours=3 limit=4 cells=001200000212100022111002222222220002222000011000000110001211112212111222121122221100200011000000",
            "mosaic 2026-09-30 HARD seed=1981274592409587473 9x14 colours=4 limit=5 cells=122211133122200003333220002333220002032222302022222311002233311000200011001100012221110222111111222113133002333330002003300033",
            "mosaic 2026-09-30 EXPERT seed=-3418548412472790261 10x16 colours=5 limit=6 cells=3332331111322233111122200322112200332214111133224411112000440144200011044420211134422222113311110001331111403333300043334210004333221100011122200031112220033111",
            "mosaic 2027-02-28 STANDARD seed=5380952429797751608 8x12 colours=3 limit=4 cells=111222221102222210021200100011000000112200000011200001112111201121122000211220002000000020000111",
            "mosaic 2027-02-28 HARD seed=-528819274515188237 9x14 colours=4 limit=5 cells=222333302222111222222111222232110022130000022110001122122011122122211122122211000332330000003333022000000333000310333033310333",
            "mosaic 2027-02-28 EXPERT seed=-6576542717320717307 10x16 colours=5 limit=6 cells=2111444111231144444433114424441111222440111133334411113311400000331140002211111000221111400022111444444212024444433002232223300222444333022244444113334444411333",
            "atoms 2026-01-01 STANDARD seed=7324396860886536030 n=7 atoms=4,5,3;4,1,5;6,1,4;2,5,4;6,3,4;2,1,3;0,5,4;2,4,1;0,3,2;6,5,2 pairs=0-1h;0-3v;0-9v;1-2v;1-5v;2-4h;3-6v;3-7h;4-8v;4-9h;5-7h;6-8h solution=120222200212",
            "atoms 2026-01-01 HARD seed=6633772894390796075 n=9 atoms=2,5,5;2,8,2;2,2,5;5,5,6;5,2,2;2,0,3;8,5,2;6,0,3;6,2,3;6,8,1;6,4,3;0,2,1;8,4,4;4,2,1;0,5,1;8,0,2 pairs=0-1h;0-2h;0-3v;0-14v;1-9v;2-5h;2-11v;2-13v;3-4h;3-6v;4-8v;4-13v;5-7v;6-12h;7-8h;7-15v;8-10h;9-10h;10-12v;11-14h;12-15h solution=112112112200102010202",
            "atoms 2026-01-01 EXPERT seed=-2433817392657962930 n=11 atoms=3,10,4;7,10,3;7,8,6;4,8,3;1,10,3;4,4,5;3,7,3;2,4,6;9,8,5;9,10,1;3,5,1;9,6,5;4,1,4;2,7,2;1,6,3;1,2,2;6,1,4;6,6,2;1,0,1;6,4,1;9,3,1;2,2,2;8,1,2;8,5,1 pairs=0-1v;0-4v;0-6h;1-2h;1-9v;2-3v;2-8v;3-5h;4-14h;5-7v;5-12h;5-19v;6-10h;6-13v;7-13h;7-21h;8-9h;8-11h;10-23v;11-17v;11-20h;12-16v;14-15h;14-17v;15-18h;15-21v;16-19h;16-22v;17-19h;22-23h solution=112202212220102212021210101101",
            "atoms 2026-09-30 STANDARD seed=4562478289292170 n=7 atoms=5,3,4;5,0,3;3,0,5;1,0,1;3,3,3;5,6,2;3,6,3;3,4,2;3,2,2;0,3,1 pairs=0-1h;0-4v;0-5h;1-2v;2-3v;2-8h;4-7h;4-8h;4-9v;5-6v;6-7h solution=12121200112",
            "atoms 2026-09-30 HARD seed=2033710288793218200 n=9 atoms=2,0,2;2,4,4;2,6,5;2,8,1;4,4,4;7,4,4;4,8,3;7,6,2;0,6,2;6,0,1;8,8,3;5,6,1;4,1,1;8,5,2;8,3,1;7,0,2 pairs=0-1h;0-9v;1-2h;1-4v;2-3h;2-8v;2-11v;3-6v;4-5v;4-6h;4-12h;5-7h;5-15h;6-10v;7-11v;9-15v;10-13h;13-14h solution=112112001111221011",
            "atoms 2026-09-30 EXPERT seed=6723743073781984435 n=11 atoms=10,3,4;6,3,4;4,3,7;4,6,2;6,6,3;6,10,3;4,1,4;9,6,1;2,3,3;2,7,5;1,1,3;6,1,2;10,6,3;1,5,2;9,1,1;4,7,1;10,9,3;4,10,3;0,7,5;1,10,1;7,9,2;10,0,1;0,5,2;0,9,1 pairs=0-1v;0-12h;0-21h;1-2v;1-4h;1-11h;2-3h;2-6h;2-8v;3-4v;3-15h;4-5h;4-7v;5-17v;6-10v;6-11v;7-12v;7-14h;8-9h;9-15v;9-18v;10-13h;11-14v;12-16h;13-19h;13-22v;15-17h;16-20v;17-19v;18-22h;18-23h;20-23v solution=12121022100112110021221100021210",
            "atoms 2027-02-28 STANDARD seed=3482774166404066734 n=7 atoms=3,1,4;3,3,6;3,5,4;0,3,4;0,1,2;6,5,2;1,5,1;1,1,2;5,3,2;0,5,1 pairs=0-1h;0-7v;1-2h;1-3v;1-8v;2-5v;2-6v;3-4h;3-9h;4-7v;6-7h;6-9v solution=221122121000",
            "atoms 2027-02-28 HARD seed=-2330801630341486955 n=9 atoms=5,2,4;7,2,6;7,4,2;5,5,4;5,7,6;7,0,3;4,0,3;1,0,2;1,3,2;2,7,5;0,7,3;4,2,1;8,7,2;0,5,2;2,5,2;1,5,1 pairs=0-1v;0-3h;0-11v;1-2h;1-5h;3-4h;3-14v;4-9v;4-12v;5-6v;6-7v;6-11h;7-8h;8-15h;9-10v;9-14h;10-13h;13-15v;14-15v solution=2202220221111112200",
            "atoms 2027-02-28 EXPERT seed=5378805927608065904 n=11 atoms=10,8,4;10,6,4;10,3,5;8,3,7;7,8,1;8,0,2;8,6,3;10,1,2;6,6,5;6,9,3;5,3,4;3,9,4;1,9,4;1,7,6;4,0,3;10,10,1;5,7,2;1,3,2;8,5,4;2,0,2;5,5,4;2,6,2;3,5,2;1,4,2 pairs=0-1h;0-4v;0-15h;1-2h;1-6v;2-3v;2-7h;3-5h;3-10v;3-18h;5-14v;6-8v;6-18h;8-9h;8-21v;9-11v;10-17v;10-20h;11-12v;11-22h;12-13h;13-16v;13-23h;14-19v;16-20h;17-23h;18-20v;19-21h;20-22v solution=21111221221201222020222200202",
        )
    }
}
