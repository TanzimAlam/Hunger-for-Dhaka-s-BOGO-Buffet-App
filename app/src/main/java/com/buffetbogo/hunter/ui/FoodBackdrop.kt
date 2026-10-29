package com.buffetbogo.hunter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buffetbogo.hunter.ui.theme.BuffetColors
import com.buffetbogo.hunter.ui.theme.PlayfulFoodBackdrop

/**
 * A scatter of food across the page — the temporary, eye-catching backdrop.
 *
 * Draw it behind a screen's content with `Modifier.fillMaxSize().foodBackdrop()`. It paints
 * three soft washes (saffron, mint, tomato) and pre-measured emoji glyphs on a jittered grid.
 * Everything the user reads sits on opaque white cards, chips or fields, so the doodles only
 * show in the gutters, around the edges and in the header band, where a short white fade keeps
 * the title crisp.
 *
 * This is decoration only: it takes no input, holds no state and can be removed by turning
 * [com.buffetbogo.hunter.ui.theme.PlayfulFoodBackdrop] off.
 */
@Composable
fun Modifier.foodBackdrop(): Modifier {
    if (!PlayfulFoodBackdrop) return this
    val measurer = rememberTextMeasurer()
    val glyphs = remember(measurer) {
        FOOD_DOODLES.mapIndexed { index, food ->
            measurer.measure(
                text = food,
                style = TextStyle(fontSize = (22 + (index * 5) % 10).sp),
            )
        }
    }
    return drawBehind { drawScatter(glyphs) }
}

private fun DrawScope.drawScatter(glyphs: List<TextLayoutResult>) {
    val shortSide = size.minDimension

    // Warm paper with a little saffron at the top and mint at the bottom right.
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFF4E2),
                Color(0xFFFFFDF8),
                Color(0xFFEAF6EE),
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFC07A).copy(alpha = 0.26f), Color.Transparent),
            center = Offset(size.width * 0.12f, size.height * 0.06f),
            radius = shortSide * 0.62f,
        ),
        radius = shortSide * 0.62f,
        center = Offset(size.width * 0.12f, size.height * 0.06f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF8FD3A4).copy(alpha = 0.20f), Color.Transparent),
            center = Offset(size.width * 0.98f, size.height * 0.48f),
            radius = shortSide * 0.66f,
        ),
        radius = shortSide * 0.66f,
        center = Offset(size.width * 0.98f, size.height * 0.48f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFF9B7A).copy(alpha = 0.18f), Color.Transparent),
            center = Offset(size.width * 0.28f, size.height * 1.02f),
            radius = shortSide * 0.58f,
        ),
        radius = shortSide * 0.58f,
        center = Offset(size.width * 0.28f, size.height * 1.02f),
    )

    // Pre-measured glyphs on an alternating grid; every wobble is derived from the index so the
    // pattern is stable across recompositions and scrolls.
    val cellW = 84.dp.toPx()
    val cellH = 96.dp.toPx()
    var index = 0
    var row = 0
    var y = -cellH * 0.4f
    while (y < size.height + cellH * 0.5f) {
        var x = if (row % 2 == 0) -cellW * 0.25f else -cellW * 0.75f
        while (x < size.width + cellW * 0.5f) {
            val glyph = glyphs[index % glyphs.size]
            val wobble = ((index * 37 + row * 13) % 101) / 100f
            val dx = (wobble - 0.5f) * cellW * 0.42f
            val dy = (((index * 61 + row * 7) % 97) / 100f - 0.5f) * cellH * 0.34f
            val topLeft = Offset(x + dx, y + dy)
            val pivot = Offset(
                topLeft.x + glyph.size.width / 2f,
                topLeft.y + glyph.size.height / 2f,
            )
            rotate(degrees = wobble * 32f - 16f, pivot = pivot) {
                drawText(
                    textLayoutResult = glyph,
                    topLeft = topLeft,
                    alpha = 0.20f + wobble * 0.16f,
                )
            }
            index++
            x += cellW
        }
        row++
        y += cellH
    }

    // The brand title sits straight on the page; a short white fade keeps it crisp.
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFFDF8).copy(alpha = 0.82f),
                Color(0xFFFFFDF8).copy(alpha = 0.35f),
                Color.Transparent,
            ),
            startY = 0f,
            endY = 168.dp.toPx(),
        ),
        size = Size(size.width, 168.dp.toPx()),
    )
}

/** The dishes a Dhaka buffet is made of, scattered as decoration. */
private val FOOD_DOODLES = listOf(
    "🍛", "🍔", "🍕", "🍤", "🥘", "🍜",
    "🍩", "🥗", "🍰", "🍗", "🥟", "☕",
    "🥭", "🍉", "🌶️", "🧋", "🍢", "🥞",
)
