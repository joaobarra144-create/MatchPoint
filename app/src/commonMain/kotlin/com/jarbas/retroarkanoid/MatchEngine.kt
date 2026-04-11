package com.jarbas.retroarkanoid

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.random.Random

private const val VW    = 1000f
private const val VH    = 1600f
private const val NET_Y = 800f

enum class PointPhase { SERVING, RALLY, POINT_OVER }

// ─────────────────────────────────────────────────────────────────────────────
// Predição de queda da bola
// ─────────────────────────────────────────────────────────────────────────────
fun predictBallLanding(
    pos:     Offset,
    vel:     Offset,
    ballZ:   Float,
    zVel:    Float,
    gravity: Float,
    targetY: Float
): Offset? {
    var x  = pos.x; var y  = pos.y
    var z  = ballZ; var vz = zVel
    val vx = vel.x; val vy = vel.y

    val goingToTarget =
        if (targetY < NET_Y) vy < 0f else vy > 0f
    if (!goingToTarget) return null

    repeat(500) {
        x += vx; y += vy
        if (z > 0f) {
            vz -= gravity
        } else if (vz < -1f) {
            z  = 0f
            vz = abs(vz) * 0.55f
        }
        z += vz
        if (z < 0f) z = 0f

        val reached =
            if (targetY < NET_Y) y <= targetY
            else                 y >= targetY
        if (reached) return Offset(
            x.coerceIn(80f, VW - 80f), targetY
        )
    }
    return null
}

// ─────────────────────────────────────────────────────────────────────────────
// AiController
//
// cpuLobBlocked:
//   Activo quando o jogador faz LOB.
//   Impede a CPU de bater ATÉ a bola ressaltar uma vez no campo da CPU.
//   Após o ressalto a bola sobe alto (vz_bounce = vz_in*0.55 ainda alto)
//   e a CPU ainda não consegue bater porque ballZ > cpuMaxZ no GameScreen.
//   O resultado: bola ressalta 2x → ponto do jogador.
//
// lastPlayerShot:
//   Actualizado pelo GameScreen no momento do hit do jogador.
//   Usado para ajustar reacção da CPU.
// ─────────────────────────────────────────────────────────────────────────────
class AiController(private val cfg: DifficultyConfig) {

    private var targetX        = 500f
    private var reactionFrames = 0

    var lastPlayerShot: ShotType = ShotType.NORMAL

    // Flag: CPU bloqueada de bater enquanto Lob ainda não ressaltou
    var cpuLobBlocked: Boolean = false

    fun reset() {
        targetX        = 500f
        reactionFrames = 0
        lastPlayerShot = ShotType.NORMAL
        cpuLobBlocked  = false
    }

    // Chamado pelo GameScreen quando a bola ressalta no campo da CPU
    fun onBounceCpuSide() {
        // Após 1 ressalto no campo da CPU, desbloqueia tentativa de bater
        // (mas ballZ ainda será alto demais para bater com sucesso)
        cpuLobBlocked = false
    }

    fun update(
        cpuX:            Float,
        cpuY:            Float,
        ballPos:         Offset,
        ballVel:         Offset,
        ballZ:           Float,
        ballZVel:        Float,
        gravity:         Float,
        ballMovingToCpu: Boolean
    ): Float {
        if (ballMovingToCpu) {
            when (lastPlayerShot) {

                // AMORTIE: CPU não avança — bola já caiu curta no seu campo
                ShotType.AMORTIE -> {
                    targetX = 500f
                }

                // LOB: CPU move-se para onde a bola vai cair mas com muita imprecisão
                ShotType.LOB -> {
                    if (reactionFrames > 0) {
                        reactionFrames--
                    } else {
                        val landing = predictBallLanding(
                            ballPos, ballVel, ballZ, ballZVel, gravity, cpuY
                        )
                        targetX = landing?.x ?: ballPos.x
                        val lobError = when {
                            cfg.cpuSpeed < 6f  -> 150f
                            cfg.cpuSpeed < 10f -> 100f
                            else               ->  70f
                        }
                        targetX +=
                            Random.nextFloat() * lobError * 2f - lobError
                        targetX = targetX.coerceIn(80f, VW - 80f)
                        reactionFrames = when {
                            cfg.cpuSpeed < 6f  -> 14
                            cfg.cpuSpeed < 10f ->  9
                            else               ->  4
                        }
                    }
                }

                // NORMAL, LEFT, RIGHT: comportamento padrão
                else -> {
                    if (reactionFrames > 0) {
                        reactionFrames--
                    } else {
                        val landing = predictBallLanding(
                            ballPos, ballVel, ballZ, ballZVel, gravity, cpuY
                        )
                        targetX = landing?.x ?: ballPos.x
                        val errorRange = when {
                            cfg.cpuSpeed < 6f  -> 80f
                            cfg.cpuSpeed < 10f -> 45f
                            else               -> 20f
                        }
                        targetX +=
                            Random.nextFloat() * errorRange * 2f - errorRange
                        targetX = targetX.coerceIn(80f, VW - 80f)
                        reactionFrames = when {
                            cfg.cpuSpeed < 6f  -> 8
                            cfg.cpuSpeed < 10f -> 4
                            else               -> 1
                        }
                    }
                }
            }
        } else {
            // Bola a afastar → regressa ao centro
            targetX = 500f
        }

        val delta = (targetX - cpuX).coerceIn(-cfg.cpuSpeed, cfg.cpuSpeed)
        return (cpuX + delta).coerceIn(80f, VW - 80f)
    }

    fun chooseShotTarget(p1X: Float): Pair<Float, ShotType> {
        val base = if (p1X > 500f)
            120f + Random.nextFloat() * 260f
        else
            620f + Random.nextFloat() * 260f

        val shot = if (cfg.cpuSpeed >= 12f && Random.nextFloat() < 0.15f)
            if (Random.nextBoolean()) ShotType.LOB else ShotType.AMORTIE
        else
            ShotType.NORMAL

        return Pair(base.coerceIn(110f, 890f), shot)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Validação do quadrado de serviço
// ─────────────────────────────────────────────────────────────────────────────
fun isServeInBox(
    ballPos:        Offset,
    serverPlayer:   Int,
    serveFromRight: Boolean
): Boolean {
    return if (serverPlayer == 1) {
        val xOk =
            if (serveFromRight) ballPos.x in 100f..500f
            else                ballPos.x in 500f..900f
        ballPos.y in 450f..800f && xOk
    } else {
        val xOk =
            if (serveFromRight) ballPos.x in 500f..900f
            else                ballPos.x in 100f..500f
        ballPos.y in 800f..1150f && xOk
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// computePlayerHit — física por tipo de shot
//
// AMORTIE:
//   vy=-22f  → rápido o suficiente para passar a rede (600 px/speed=~15f/frame)
//   vz=2f    → quase sem altura → bola não sobe → cai logo após a rede (y≈550-650)
//   vx curto → pouca velocidade lateral → bola cai no centro do campo da CPU
//   Resultado: 2 ressaltos rápidos no campo da CPU antes de ela se mover.
//
// LOB:
//   vy=-14f  → velocidade intermédia, chega ao campo da CPU
//   vz=28f   → sobe muito alto (pico z≈400+)
//   Após ressaltar 1x no campo da CPU: vz_bounce = 28*0.55≈15f → sobe de novo
//   CPU tem ballZ > 60f quando tenta bater → bloqueada pelo cpuLobBlocked
//   Resultado: bola ressalta 2x → ponto do jogador.
// ─────────────────────────────────────────────────────────────────────────────
fun computePlayerHit(
    ballPos: Offset,
    p1X:     Float,
    shot:    ShotType,
    speed:   Float
): Pair<Offset, Float> {
    return when (shot) {

        ShotType.LOB -> {
            val vx = safeHitVx(ballPos.x, p1X) * 0.4f * speed
            val vy = -14f * speed
            val vz = 28f  * speed
            Pair(Offset(vx, vy), vz)
        }

        ShotType.AMORTIE -> {
            // Velocidade suficiente para passar a rede + queda curta
            val vx = safeHitVx(ballPos.x, p1X) * 0.3f * speed
            val vy = -22f * speed
            val vz = 2f   * speed
            Pair(Offset(vx, vy), vz)
        }

        ShotType.LEFT -> {
            // Mirar para perto da linha esquerda do campo adversário (dentro dos limites)
            val targetX = 150f
            val frames  = 40f  // frames estimadas até ao ressalto
            val vx = ((targetX - ballPos.x) / frames).coerceIn(-12f, -2f) * speed
            val vy = -20f * speed
            val vz = 14f  * speed
            Pair(Offset(vx, vy), vz)
        }

        ShotType.RIGHT -> {
            // Mirar para perto da linha direita do campo adversário (dentro dos limites)
            val targetX = 850f
            val frames  = 40f
            val vx = ((targetX - ballPos.x) / frames).coerceIn(2f, 12f) * speed
            val vy = -20f * speed
            val vz = 14f  * speed
            Pair(Offset(vx, vy), vz)
        }

        ShotType.NORMAL -> {
            val vx = safeHitVx(ballPos.x, p1X) * speed
            val vy = -20f * speed
            val vz = 14f  * speed
            Pair(Offset(vx, vy), vz)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// computeCpuHit — física de hit da CPU (P2) por tipo de shot
// ─────────────────────────────────────────────────────────────────────────────
fun computeCpuHit(
    p2X:     Float,
    targetX: Float,
    shot:    ShotType,
    speed:   Float
): Pair<Offset, Float> {
    val rawDx = (targetX - p2X) / 55f
    val vx    = rawDx.coerceIn(-10f, 10f) * speed

    return when (shot) {
        ShotType.LOB     -> Pair(Offset(vx * 0.45f, 12f * speed), 26f * speed)
        ShotType.AMORTIE -> Pair(Offset(vx * 0.35f, 18f * speed), 6f  * speed)
        else             -> Pair(Offset(vx,          20f * speed), 14f * speed)
    }
}
