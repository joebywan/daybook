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

/**
 * Mosaic and Atoms on the web route, pinned the same way [WebParityTest] pins Kings.
 *
 * The web page prints these lines, in this format, under `?dump`; [WebParityDumpTest] writes the
 * JVM's side of a whole year for diffing against the browser — and against an earlier commit.
 *
 * The Atoms lines were re-pinned when the generator took longer bonds, denser Hard and Expert boards
 * and a depth gate per tier (2026-10), which changed every Atoms board on both builds.
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
            "atoms 2026-01-01 STANDARD seed=7324396860886536030 n=7 atoms=1,5,3;1,0,2;4,5,3;4,3,4;6,3,5;6,6,4;6,0,4;2,0,2;2,3,1;2,6,2 pairs=0-1h;0-2v;1-7v;2-3h;3-4v;3-8v;4-5h;4-6h;5-9v;6-7v;7-8h;8-9h solution=210211222200",
            "atoms 2026-01-01 HARD seed=6633772894390796075 n=9 atoms=4,4,5;2,4,6;6,4,4;2,1,3;4,7,5;6,0,2;7,7,4;2,6,4;7,1,2;0,7,1;0,6,3;0,4,2;6,6,2;1,0,2;1,3,2;4,1,1;1,5,1;0,0,1 pairs=0-1v;0-2v;0-4h;0-15h;1-3h;1-7h;1-11v;2-5h;2-12h;3-15v;4-6v;4-9v;5-13v;6-8h;7-10v;7-12v;8-15v;9-10h;10-11h;11-17h;13-14h;13-17v;14-16h solution=21202201212112200011101",
            "atoms 2026-01-01 EXPERT seed=-2433817392657962930 n=11 atoms=3,10,5;3,6,6;5,6,5;5,9,3;8,10,3;3,1,5;5,1,5;5,3,3;9,9,3;0,1,4;9,6,1;1,6,3;1,9,2;9,7,3;0,3,4;0,10,2;0,6,1;8,3,2;8,5,4;5,5,1;10,10,3;7,7,1;10,6,1;10,5,3;2,3,3;10,1,1;10,3,1;2,5,2 pairs=0-1h;0-4v;0-15v;1-2v;1-5h;1-11v;2-3h;2-10v;2-19h;3-8v;3-12v;4-18h;4-20v;5-6v;5-9v;6-7h;6-25v;7-17v;7-19h;7-24v;8-13h;9-14h;10-13h;10-22v;11-12h;11-16v;13-21v;14-16h;14-24v;15-16h;17-18h;17-26v;18-19v;18-23v;19-27v;20-22h;22-23h;23-26h;24-27h;25-26h solution=2122112101002222110022002011101012010120",
            "atoms 2026-09-30 STANDARD seed=4562478289292170 n=7 atoms=5,3,5;0,3,5;0,6,3;5,5,4;1,5,2;3,6,2;5,0,3;0,0,1;6,6,1;2,0,2 pairs=0-1v;0-3h;0-6h;1-2h;1-7h;2-5v;3-4v;5-8v;6-9v;7-9v solution=2212112120",
            "atoms 2026-09-30 HARD seed=2033710288793218200 n=9 atoms=8,2,5;8,7,2;8,0,3;3,2,4;4,0,3;2,0,5;2,5,5;3,4,4;6,4,3;6,7,3;5,5,1;3,7,2;0,0,3;0,4,3;2,8,5;0,6,2;4,8,1;0,8,2 pairs=0-1h;0-2h;0-3v;1-9v;2-4v;3-7h;4-5v;4-16h;5-6h;5-12v;6-10v;6-14h;7-8v;7-11h;7-13v;8-9h;9-11v;12-13h;13-15h;14-16v;14-17v;15-17h solution=2120221022122001212120",
            "atoms 2026-09-30 EXPERT seed=6723743073781984435 n=11 atoms=2,0,5;2,3,7;6,0,2;0,0,4;0,3,4;7,3,5;2,7,4;0,6,4;7,5,6;0,9,4;9,5,2;6,9,2;7,9,1;5,5,4;7,0,2;5,7,2;9,9,1;3,5,2;9,3,3;10,0,2;10,4,3;6,2,3;10,6,3;0,2,2;9,1,2;4,2,2;10,10,3;6,10,2 pairs=0-1h;0-2v;0-3v;1-4v;1-5v;1-6h;2-14v;2-21h;3-23h;4-7h;4-23h;5-8h;5-14h;5-18v;6-15v;7-9h;7-22v;8-10v;8-12h;8-13v;9-11v;10-16h;10-18h;11-12v;11-21h;11-27h;12-16v;13-15h;13-17v;14-19v;18-24h;19-20h;20-22h;21-25v;22-26h;23-25v;26-27v solution=2122120122021122011221000000212122102",
            "atoms 2027-02-28 STANDARD seed=3482774166404066734 n=7 atoms=1,1,4;3,1,2;1,6,3;3,6,4;3,2,2;5,2,3;5,0,4;1,0,2;5,6,3;5,4,1 pairs=0-1v;0-2h;0-7h;1-4h;2-3v;3-4h;3-8v;4-5v;5-6h;5-9h;6-7v;8-9h solution=220011212021",
            "atoms 2027-02-28 HARD seed=-2330801630341486955 n=9 atoms=4,0,5;2,0,3;4,3,5;2,6,1;7,0,3;7,4,5;7,7,1;4,5,4;4,8,6;6,5,3;6,8,2;6,7,2;2,8,3;6,3,2;0,8,2;0,6,3;0,4,2;5,4,2 pairs=0-1v;0-2h;0-4v;1-3h;2-7h;2-13v;3-12h;3-15v;4-5h;5-6h;5-17v;6-11v;7-8h;7-9v;8-10v;8-12v;9-11h;9-13h;10-11h;12-14v;14-15h;15-16h;16-17v solution=22111200212021222001120",
            "atoms 2027-02-28 EXPERT seed=5378805927608065904 n=11 atoms=10,9,4;10,6,4;8,9,4;8,3,4;10,4,4;3,9,2;2,3,3;8,0,3;3,0,3;10,0,2;2,6,4;2,1,1;1,0,2;1,6,2;7,6,4;7,4,3;2,9,1;1,10,3;3,2,2;7,8,3;4,10,3;9,10,1;4,8,2;5,2,3;3,4,2;3,7,2;7,2,2;5,7,1 pairs=0-1h;0-2v;1-4h;1-14v;2-3h;2-5v;3-6v;3-7h;4-9h;4-15v;5-16v;5-25h;6-10h;6-11h;7-8v;7-9v;8-12v;8-18h;10-13v;10-14v;10-16h;12-13h;13-17h;14-15h;14-19h;15-24v;15-26h;17-20v;18-23v;18-24h;19-22v;20-21v;20-22h;23-26v;23-27h;24-25h;25-27v solution=2220111220011110110211111202102102001",
        )
    }
}
