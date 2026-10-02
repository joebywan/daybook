package com.joebywan.daybook.ui.teach

import androidx.compose.ui.geometry.Rect

/** Where [placePopover] put the hint popover: its top edge, its height, and whether it had to shrink. */
internal data class PopoverSpot(val y: Float, val height: Float, val limited: Boolean)

/**
 * Overlap with a control the player needs (a digit pad, a palette) weighs this much per pixel of
 * height, against 4 for the highlight: in effect a constraint. Covering the pad means the move the
 * hint describes cannot be made while the explanation is up, which is worse than covering what the
 * hint points at (the text names it), however much of it. It is a weight rather than a rule so
 * that, were every place to cover a control, the least bad one still wins.
 */
private const val KEEP_CLEAR_WEIGHT = 1000f
private const val HIGHLIGHT_WEIGHT = 4f

/**
 * Chooses the popover's vertical position and height; pure, in window pixels, so the placement
 * rules can be tested without a screen. [highlight] is already padded, null when unknown (below).
 *
 * Three places are tried. *Above*: hugging the top of the board area, rising as far as [minTop] to
 * clear a highlight. *Below*: hugging [maxBottom], the top of the toolbar. *Clear*: just above the
 * topmost [keepClear] control, for when the controls sit between the board and the toolbar and
 * "below" would be on top of them. Each is costed by what it would cover, and the cheapest wins;
 * a tie goes to the old rule (above only when the highlight is in the lower half and nothing is
 * covered, otherwise below), and "clear" must be strictly cheaper than the pick to replace it. If the winner still covers something at full
 * [natural] height, the popover shrinks to [compact] (its text scrolls) and the choice is made again.
 */
internal fun placePopover(
    natural: Float,
    compact: Float,
    highlight: Rect?,
    keepClear: Collection<Rect>,
    minTop: Float,
    maxBottom: Float,
    boardTop: Float,
    gap: Float,
    windowHeight: Float,
): PopoverSpot {
    fun cost(y: Float, h: Float): Float {
        fun overlap(r: Rect) = maxOf(0f, minOf(y + h, r.bottom) - maxOf(y, r.top))
        val onHighlight = if (highlight == null) 0f else overlap(highlight) * HIGHLIGHT_WEIGHT
        return onHighlight + keepClear.sumOf { overlap(it).toDouble() }.toFloat() * KEEP_CLEAR_WEIGHT
    }
    val keepTop = keepClear.minOfOrNull { it.top }

    fun choose(h: Float): Pair<Float, Float> {
        val aboveY = maxOf(minOf(boardTop + gap, if (highlight == null) Float.MAX_VALUE else highlight.top - h - gap), minTop)
        val belowY = maxBottom - h
        val aboveCost = cost(aboveY, h)
        val belowCost = cost(belowY, h)
        val up = when {
            highlight == null -> false
            aboveCost != belowCost -> aboveCost < belowCost
            else -> (highlight.top + highlight.bottom) / 2f > windowHeight / 2f && aboveCost == 0f
        }
        val pickY = if (up) aboveY else belowY
        val pickCost = if (up) aboveCost else belowCost
        if (keepTop != null && pickCost > 0f) {
            val clearY = maxOf(minOf(keepTop - gap - h, maxBottom - h), minTop)
            val clearCost = cost(clearY, h)
            if (clearCost < pickCost) return clearY to clearCost
        }
        return pickY to pickCost
    }

    val full = choose(natural)
    val limited = full.second > 0f && natural > compact
    return if (limited) PopoverSpot(choose(compact).first, compact, true) else PopoverSpot(full.first, natural, false)
}
