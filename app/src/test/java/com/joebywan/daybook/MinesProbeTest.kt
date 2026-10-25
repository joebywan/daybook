package com.joebywan.daybook

import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.MinesLogic
import org.junit.Test
import androidx.compose.ui.graphics.toArgb

class MinesProbeTest {
    @Test fun accent() {
        val modes = listOf(null, ColourDistance.deut, ColourDistance.prot, ColourDistance.trit)
        val accents = com.joebywan.daybook.core.PuzzleRegistry.all.map { androidx.compose.ui.graphics.Color(it.accent) } +
            androidx.compose.ui.graphics.Color(0xFF3C28BD)
        fun nearest(c: androidx.compose.ui.graphics.Color, m: Array<DoubleArray>?) = accents.minOf { ColourDistance.de2000(c, it, m) }
        val all = (0 until 360 step 2).flatMap { h -> (37..73 step 4).flatMap { s -> (45..67 step 4).map { l ->
            androidx.compose.ui.graphics.Color.hsl(h.toFloat(), s / 100f, l / 100f) } } }
        for (c in all.sortedByDescending { c -> modes.minOf { nearest(c, it) } }.take(8))
            println("ACCENT 0x%08X %.1f".format(c.toArgb(), modes.minOf { nearest(c, it) }))
    }

    @Test fun tutorial() {
        var found = 0
        for (k in 0 until 200000) {
            if (found >= 6) break
            val rng = Rng(77L + k)
            val w = 5; val h = 5
            val opening = rng.nextInt(w * h)
            val clear = MinesLogic.neighbours(w, h, opening).toList() + opening
            val pool = (0 until w * h).filter { it !in clear }
            val chosen = rng.shuffled(pool).take(4).toSet()
            val mines = CharArray(w * h) { if (it in chosen) '1' else '0' }.concatToString()
            var s = com.joebywan.daybook.puzzles.MinesState(w, h, mines, opening, MinesLogic.openingMarks(w, h, mines, opening))
            if (MinesLogic.walk(w, h, mines, opening)?.depth != 1) continue
            if (s.marks.count { it == '.' } - 4 < 5) continue
            val t1 = com.joebywan.daybook.puzzles.MinesTeacher.teach(s)!!
            if (t1.technique != "all-mines" || t1.mines.size != 1) continue
            s = s.reveal(t1.mines.first())!!
            val t2 = com.joebywan.daybook.puzzles.MinesTeacher.teach(s)!!
            if (t2.technique != "all-found") continue
            s = com.joebywan.daybook.puzzles.MinesTeacher.apply(s, t2)
            val t3 = com.joebywan.daybook.puzzles.MinesTeacher.teach(s) ?: continue
            if (t3.technique != "all-mines" || t3.mines.size != 1) continue
            s = com.joebywan.daybook.puzzles.MinesTeacher.apply(s, t3)
            if (s.solved) continue
            val left = s.marks.indices.count { !s.isMine(it) && !s.isOpen(it) }
            if (left < 2) continue
            found++
            println("TUT k=$k opening=$opening mines=$mines t1=${t1.mines} t2=${t2.safe} via ${t2.focus} t3=${t3.mines} left=$left")
            val s0 = com.joebywan.daybook.puzzles.MinesState(w, h, mines, opening, MinesLogic.openingMarks(w, h, mines, opening))
            for (r in 0 until h) println("TUT  " + (0 until w).joinToString(" ") { c -> val i = r * w + c; if (s0.isMine(i)) "*" else s0.number(i).toString() } + "    " + (0 until w).joinToString(" ") { c -> s0.marks[r * w + c].toString() })
        }
    }

    @Test fun variants() {
        val boards = HashMap<String, Int>()
        for (seed in 1L..300L) {
            val s = com.joebywan.daybook.puzzles.Mines.generate(seed, com.joebywan.daybook.core.Difficulty.EXPERT) as com.joebywan.daybook.puzzles.MinesState
            val n = s.width * s.height
            val m = MinesLogic.openingMarks(s.width, s.height, s.mines, s.opening).toCharArray()
            val num = IntArray(n) { -1 }; val st = IntArray(n)
            fun rev(i: Int) { num[i] = s.number(i); st[i] = MinesLogic.SAFE }
            for (i in 0 until n) if (m[i] == 'o') rev(i)
            val kinds = HashSet<String>()
            while (true) {
                val f = MinesLogic.nextFact(s.width, s.height, num, st, s.total) ?: break
                if (f.level == 3) kinds += if (f.technique == "count") (if (f.left == 0) "count-zero" else if (f.cited.isEmpty()) "count-all" else "count-edge") else "what-if"
                for (i in f.mines) st[i] = MinesLogic.MINE
                for (i in f.safe) for (j in MinesLogic.openInPlace(s.width, s.height, s.mines, m, i)) rev(j)
            }
            val key = kinds.sorted().joinToString("+")
            boards[key] = (boards[key] ?: 0) + 1
        }
        println("VAR $boards")
    }

    @Test fun texts() {
        val seen = HashMap<String, Int>()
        for (seed in 1L..60L) for (t in com.joebywan.daybook.core.Difficulty.entries) {
            var s = com.joebywan.daybook.puzzles.Mines.generate(seed, t) as com.joebywan.daybook.puzzles.MinesState
            while (!s.solved) {
                val st = com.joebywan.daybook.puzzles.MinesTeacher.teach(s)!!
                val k = st.technique + (if (st.safe.isEmpty()) "-m" else "-s")
                if ((seen[k] ?: 0) < 3) { seen[k] = (seen[k] ?: 0) + 1; println("TXT $k | ${st.nudge} | ${st.explanation}") }
                s = com.joebywan.daybook.puzzles.MinesTeacher.apply(s, st)
            }
        }
    }

    @Test fun probe() {
        val specs = (System.getenv("MINES_SPECS") ?: "8,8,10;8,10,16;8,12,22").split(";").map { it.split(",").map(String::toInt) }
        for (sp in specs) {
            val (w, h, m) = sp
            val depthCount = IntArray(5)
            var stall = 0
            val openShares = ArrayList<Int>()
            val t0 = System.nanoTime()
            val n = 2000
            for (k in 0 until n) {
                val rng = Rng(1000L + k * 7919L)
                val opening = rng.nextInt(w * h)
                val clear = MinesLogic.neighbours(w, h, opening).toList() + opening
                val pool = (0 until w * h).filter { it !in clear }
                val chosen = rng.shuffled(pool).take(m).toSet()
                val mines = CharArray(w * h) { if (it in chosen) '1' else '0' }.concatToString()
                val marks = MinesLogic.openingMarks(w, h, mines, opening)
                openShares += marks.count { it == 'o' } * 1000 / (w * h - m)
                val walk = MinesLogic.walk(w, h, mines, opening)
                if (walk == null) stall++ else depthCount[walk.depth]++
            }
            val ms = (System.nanoTime() - t0) / 1e6 / n
            openShares.sort()
            println("SPEC ${w}x$h/$m: stall=$stall depth=${depthCount.toList()} opening p10/50/90=${openShares[n / 10]}/${openShares[n / 2]}/${openShares[n * 9 / 10]} ms/draw=%.2f".format(ms))
        }
    }
}
