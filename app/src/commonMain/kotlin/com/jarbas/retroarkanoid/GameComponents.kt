package com.jarbas.retroarkanoid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

enum class ShotType { NORMAL, LEFT, RIGHT, LOB, AMORTIE }
enum class Difficulty { BEGINNER, INTERMEDIATE, PRO }

data class DifficultyConfig(
    val label: String,
    val speed: Float,
    val gravity: Float,
    val cpuSpeed: Float,
    val cpuHitRange: Float,
    val playerHitMargin: Float,
    val gameLoopDelay: Long
)

val difficultyConfigs = mapOf(
    Difficulty.BEGINNER     to DifficultyConfig("INICIANTE",  0.72f, 0.52f,  5f, 120f, 140f, 20),
    Difficulty.INTERMEDIATE to DifficultyConfig("INTERMÉDIO", 0.80f, 0.72f,  8f,  90f,  95f, 16),
    Difficulty.PRO          to DifficultyConfig("PRO",        1.08f, 0.90f, 12f,  75f,  70f, 16)
)

// ── Desenho do jogador ────────────────────────────────────────────────────────
fun DrawScope.drawMatchPointPlayer(
    x: Float, y: Float, scale: Float,
    animStep: Int, sX: Float, sY: Float,
    dir: Float = 1f,
    playerColor: Color = Color.Black,
    outlineMode: Boolean = false
) {
    val p = scale * sX
    val d = dir
    val strokeExtra = if (outlineMode) 3.5f else 0f
    val col = playerColor.copy(alpha = if (outlineMode) 0.9f else 1f)

    if (!outlineMode) drawOval(Color.Black.copy(0.18f), Offset(x - 22f*p, y + 80f*p), Size(44f*p, 16f*p))

    drawCircle(col, (12f + strokeExtra*0.5f)*p, Offset(x, y))

    val body = Path().apply {
        moveTo(x - (5f + strokeExtra*0.3f)*p, y + 10f*p)
        lineTo(x + (5f + strokeExtra*0.3f)*p, y + 10f*p)
        lineTo(x + (10f + strokeExtra*0.3f)*p, y + 50f*p)
        lineTo(x - (10f + strokeExtra*0.3f)*p, y + 50f*p)
        close()
    }
    drawPath(body, col)

    val lw = (8f + strokeExtra)*p
    drawLine(col, Offset(x - 5f*p, y + 50f*p),  Offset(x - 20f*p, y + 70f*p), lw)
    drawLine(col, Offset(x - 20f*p, y + 70f*p), Offset(x - 15f*p, y + 90f*p), lw)
    drawLine(col, Offset(x + 5f*p, y + 50f*p),  Offset(x + 15f*p, y + 75f*p), lw)
    drawLine(col, Offset(x + 15f*p, y + 75f*p), Offset(x + 10f*p, y + 90f*p), lw)

    when (animStep) {
        1 -> {
            drawLine(col, Offset(x + 6f*d*p, y + 20f*p), Offset(x + 20f*d*p, y - 15f*p), (7f + strokeExtra)*p)
            if (!outlineMode) drawCircle(Color.Yellow, 8f*p, Offset(x + 22f*d*p, y - 28f*p))
        }
        2 -> {
            drawLine(col, Offset(x + 6f*d*p, y + 18f*p), Offset(x + 14f*d*p, y - 40f*p), (7f + strokeExtra)*p)
            drawOval(col, Offset(x + 8f*d*p, y - 65f*p), Size(18f*p, 32f*p), style = Stroke((3f + strokeExtra)*p))
        }
        3 -> {
            drawLine(col, Offset(x + 8f*d*p, y + 22f*p), Offset(x + 38f*d*p, y + 5f*p), (7f + strokeExtra)*p)
            drawOval(col, Offset(x + 33f*d*p, y - 18f*p), Size(16f*p, 34f*p), style = Stroke((3f + strokeExtra)*p))
        }
        else -> {
            drawLine(col, Offset(x + 8f*d*p, y + 25f*p), Offset(x + 25f*d*p, y + 45f*p), (7f + strokeExtra)*p)
            drawLine(col, Offset(x + 25f*d*p, y + 45f*p), Offset(x + 35f*d*p, y + 55f*p), (4f + strokeExtra)*p)
            drawOval(col, Offset(x + 30f*d*p, y + 50f*p), Size(25f*p, 18f*p), style = Stroke((3f + strokeExtra)*p))
        }
    }
}

// ── Landing marker ────────────────────────────────────────────────────────────
fun DrawScope.drawBallLandingMarker(
    landingX: Float, landingY: Float, sX: Float, sY: Float, alpha: Float
) {
    if (alpha <= 0f) return
    val cx = landingX * sX; val cy = landingY * sY
    drawCircle(Color(0xFF00FF88).copy(alpha = alpha * 0.55f), 14f*sX, Offset(cx, cy))
    drawCircle(Color(0xFF00FF88).copy(alpha = alpha * 0.22f), 24f*sX, Offset(cx, cy))
    drawOval(Color.White.copy(alpha = alpha * 0.10f), Offset(cx - 14f*sX, cy - 5f*sY), Size(28f*sX, 10f*sY))
}

// ── Placar ─────────────────────────────────────────────────────────────────────
// jogPts = pontos do Jogador, cpuPts = pontos da CPU
fun tennisLabel(pts: Int) = when (pts) { 0 -> "0"; 1 -> "15"; 2 -> "30"; else -> "40" }

fun scoreText(jogPts: Int, cpuPts: Int): String {
    val deuce = jogPts >= 3 && cpuPts >= 3
    return when {
        deuce && jogPts == cpuPts -> "DEUCE"
        deuce && jogPts > cpuPts  -> "VAN. JOG"
        deuce && cpuPts > jogPts  -> "VAN. CPU"
        else -> "${tennisLabel(jogPts)} - ${tennisLabel(cpuPts)}"
    }
}

// ── safeHitVx ─────────────────────────────────────────────────────────────────
fun safeHitVx(ballX: Float, playerX: Float, minX: Float = 150f, maxX: Float = 850f): Float {
    val raw = (ballX - playerX) / 8f
    val clamped = raw.coerceIn(-9f, 9f)
    val predicted = ballX + clamped * 40f
    return when {
        predicted < minX -> (clamped + (minX - predicted) * 0.04f).coerceIn(-10f, 10f)
        predicted > maxX -> (clamped + (maxX - predicted) * 0.04f).coerceIn(-10f, 10f)
        else -> clamped
    }
}

// ── classifyTap ───────────────────────────────────────────────────────────────
fun classifyTap(tapX: Float, tapY: Float, playerX: Float, playerY: Float = 1350f): ShotType {
    return when {
        tapY < playerY - 180f -> ShotType.LOB
        tapY > playerY + 80f  -> ShotType.AMORTIE
        tapX < playerX - 60f  -> ShotType.LEFT
        tapX > playerX + 60f  -> ShotType.RIGHT
        else                  -> ShotType.NORMAL
    }
}
