package com.jarbas.retroarkanoid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlin.math.abs

private const val VW    = 1000f
private const val VH    = 1600f
private const val NET_Y = 800f

// Threshold de ballZ para hit da CPU em shots normais.
// Quando o jogador faz LOB, a CPU é bloqueada via cpuLobBlocked
// até a bola ressaltar — depois disso ballZ ainda é alto e ela não bate.
private const val CPU_MAX_Z = 55f

private val COLOR_COURT_BG   = Color(0xFF1B5E20)
private val COLOR_COURT_LINE = Color(0xCCFFFFFF)
private val COLOR_NET        = Color(0xFFE0E0E0)
private val COLOR_PLAYER1    = Color(0xFF000000)
private val COLOR_PLAYER2    = Color(0xFFC62828)
private val COLOR_BALL       = Color(0xFFCDDC39)
private val COLOR_HUD_BG     = Color(0xFF0D1117)

private fun p1ServeXRange(serveFromRight: Boolean) =
    if (serveFromRight) 500f..900f else 100f..500f

// ── Interop JS → Kotlin ──────────────────────────────────────────────────────
private var _externalShotSetter: ((ShotType) -> Unit)? = null

fun registerExternalShotSetter(setter: (ShotType) -> Unit) {
    _externalShotSetter = setter
}

fun setPlayerShotFromJs(shotName: String) {
    val shot = when (shotName.uppercase()) {
        "LEFT"    -> ShotType.LEFT
        "RIGHT"   -> ShotType.RIGHT
        "LOB"     -> ShotType.LOB
        "AMORTIE" -> ShotType.AMORTIE
        else      -> ShotType.NORMAL
    }
    _externalShotSetter?.invoke(shot)
}

// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MatchPointTennis(
    speaker:          ScoreSpeaker? = null,
    onSound:          ((String) -> Unit)? = null,
    touchSensitivity: Float = 1.0f
) {
    var difficulty   by remember { mutableStateOf<Difficulty?>(null) }
    val cfg           = difficulty?.let { difficultyConfigs[it] }
    val referee       = remember { RefereeManager(speaker) }
    val scope         = rememberCoroutineScope()
    var screenState   by remember { mutableStateOf(0) }

    // p1 = CPU (topo) | p2 = Jogador (fundo)
    var p1Pts   by remember { mutableStateOf(0) }
    var p2Pts   by remember { mutableStateOf(0) }
    var p1Games by remember { mutableStateOf(0) }
    var p2Games by remember { mutableStateOf(0) }
    var p1Sets  by remember { mutableStateOf(0) }
    var p2Sets  by remember { mutableStateOf(0) }

    var p1X by remember { mutableStateOf(500f) }
    var p1Y by remember { mutableStateOf(1530f) }
    var p2X by remember { mutableStateOf(500f) }
    var p2Y by remember { mutableStateOf(70f) }
    var p1RacketDir by remember { mutableStateOf(1f) }
    var p2RacketDir by remember { mutableStateOf(-1f) }

    var ballPos  by remember { mutableStateOf(Offset(500f, 1500f)) }
    var ballZ    by remember { mutableStateOf(0f) }
    var ballVel  by remember { mutableStateOf(Offset(0f, 0f)) }
    var ballZVel by remember { mutableStateOf(0f) }

    var serving          by remember { mutableStateOf(true) }
    var serverPlayer     by remember { mutableStateOf(1) }
    var serveFromRight   by remember { mutableStateOf(true) }
    var pointInProgress  by remember { mutableStateOf(false) }
    var ballAlreadyHit   by remember { mutableStateOf(false) }
    var lastHitter       by remember { mutableStateOf(0) }
    var pendingShot      by remember { mutableStateOf<ShotType?>(null) }
    var serveFault       by remember { mutableStateOf(0) }
    var serveInProgress  by remember { mutableStateOf(false) }
    var gamesPlayedInSet by remember { mutableStateOf(0) }

    // "cpu" = campo da CPU (y < NET_Y) | "player" = campo do jogador (y > NET_Y)
    // bounceCount conta ressaltos consecutivos no mesmo campo (lastBounceSide)
    var bounceCount    by remember { mutableStateOf(0) }
    var lastBounceSide by remember { mutableStateOf("") }

    var msg          by remember { mutableStateOf("") }
    var animStep     by remember { mutableStateOf(0) }
    var p2AnimStep   by remember { mutableStateOf(0) }
    var landingPos   by remember { mutableStateOf<Offset?>(null) }
    var landingAlpha by remember { mutableStateOf(0f) }
    var servePosMsg  by remember { mutableStateOf("") }

    val ai             = remember(difficulty) { cfg?.let { AiController(it) } }
    val focusRequester = remember { FocusRequester() }
    
    DisposableEffect(Unit) {
        registerExternalShotSetter { shot -> pendingShot = shot }
        onDispose { _externalShotSetter = null }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    fun resetForServer() {
        ballVel  = Offset(0f, 0f)
        ballZ    = 0f
        ballZVel = 0f
        lastHitter     = 0
        ballAlreadyHit = false
        serving        = true
        animStep       = 0
        p2AnimStep     = 0
        pendingShot    = null
        serveInProgress  = false
        bounceCount      = 0
        lastBounceSide   = ""
        landingPos       = null
        landingAlpha     = 0f
        servePosMsg      = ""
        ai?.reset()

        if (serverPlayer == 1) {
            p1X = if (serveFromRight) 700f else 300f
            p1Y = 1530f
            p2X = 500f; p2Y = 150f
            ballPos = Offset(p1X, p1Y - 30f)
        } else {
            p2X = if (serveFromRight) 300f else 700f
            p2Y = 70f
            p1X = 500f; p1Y = 1450f
            ballPos = Offset(p2X, p2Y + 30f)
        }
        p1RacketDir = if (p1X < 500f) 1f else -1f
        p2RacketDir = if (p2X < 500f) -1f else 1f
    }

    fun launchCpuServe(c: DifficultyConfig) {
        val targetX = if (p2X < 500f) 750f else 250f
        val targetY = 975f
        val vy      = 20f * c.speed
        val frames  = (targetY - p2Y) / vy
        val vx      = (targetX - p2X) / frames
        val vz      = c.gravity * frames * 0.44f
        ballVel = Offset(vx, vy)
        ballZVel = vz
        serveInProgress = true
        serving         = false
        bounceCount     = 0
        lastBounceSide  = "player"
        lastHitter      = 2
    }

    fun doFault(inNet: Boolean = false) {
        scope.launch {
            msg = if (inNet) "FALTA - NA REDE!" else "FALTA!"
            referee.saySimple(if (inNet) "Fault, net" else "Fault")
            delay(1400)
            msg            = ""
            serving        = true
            ballVel        = Offset(0f, 0f)
            ballZ          = 0f
            ballZVel       = 0f
            bounceCount    = 0
            lastBounceSide = ""
            landingPos     = null
            landingAlpha   = 0f
            if (serverPlayer == 1) {
                ballPos = Offset(p1X, p1Y - 30f)
            } else {
                val c = cfg ?: return@launch
                ballPos = Offset(p2X, p2Y + 30f)
                delay(1000)
                launchCpuServe(c)
            }
        }
    }

    fun awardPoint(winner: Int) {
        if (pointInProgress) return
        pointInProgress = true
        scope.launch {
            if (winner == 1) p1Pts++ else p2Pts++
            serveFromRight = !serveFromRight

            fun handleGameWin(w: Int) {
                if (w == 1) p1Games++ else p2Games++
                p1Pts = 0; p2Pts = 0
                gamesPlayedInSet++
                serverPlayer   = if (serverPlayer == 1) 2 else 1
                serveFromRight = true
                serveFault     = 0
            }

            if (p1Pts >= 4 && p1Pts - p2Pts >= 2) {
                handleGameWin(1)
                if (p1Games >= 6 && p1Games - p2Games >= 2) {
                    p1Sets++
                    p1Games = 0; p2Games = 0; gamesPlayedInSet = 0
                    if (p1Sets >= 2) {
                        referee.sayMatchWinner(1)
                        msg = "CPU VENCEU!"
                        delay(3500); screenState = 2
                    } else {
                        referee.saySetWinner(1)
                        msg = "SET CPU!"; delay(2500)
                    }
                } else {
                    referee.sayGameWinner(serverPlayer, 1)
                    msg = "JOGO JOGADOR!"; delay(2000)
                }
            } else if (p2Pts >= 4 && p2Pts - p1Pts >= 2) {
                handleGameWin(2)
                if (p2Games >= 6 && p2Games - p1Games >= 2) {
                    p2Sets++
                    p1Games = 0; p2Games = 0; gamesPlayedInSet = 0
                    if (p2Sets >= 2) {
                        referee.sayMatchWinner(2)
                        msg = "JOGADOR VENCEU!"
                        delay(3500); screenState = 2
                    } else {
                        referee.saySetWinner(2)
                        msg = "SET JOGADOR!"; delay(2500)
                    }
                } else {
                    referee.sayGameWinner(serverPlayer, 2)
                    msg = "JOGO CPU!"; delay(2000)
                }
            } else {
                referee.sayScore(p2Pts, p1Pts, 3 - serverPlayer)
            }

            if (screenState != 2) {
                msg             = ""
                pointInProgress = false
                resetForServer()
                val c = cfg ?: return@launch
                if (serverPlayer == 2) {
                    delay(1400); launchCpuServe(c)
                }
            }
        }
    }

    // ── Game loop ─────────────────────────────────────────────────────────────
    LaunchedEffect(screenState, difficulty) {
        val c = cfg ?: return@LaunchedEffect
        while (isActive && screenState == 1) {
            delay(c.gameLoopDelay)
            if (pointInProgress) continue
            if (!serving) {

                // Física da bola
                ballPos  += ballVel
                ballZ    += ballZVel
                if (ballZ > 0f) {
                    ballZVel -= c.gravity
                } else if (ballZVel < -1f) {
                    ballZ    = 0f
                    ballZVel = abs(ballZVel) * 0.55f
                }
                if (ballZ < 0f) ballZ = 0f

                // Predição de queda
                if (lastHitter == 1) {
                    val lp = predictBallLanding(
                        ballPos, ballVel, ballZ, ballZVel, c.gravity, 300f
                    )
                    landingPos   = lp
                    landingAlpha = if (lp != null) 0.75f else 0f
                } else {
                    val lp = predictBallLanding(
                        ballPos, ballVel, ballZ, ballZVel, c.gravity, 1300f
                    )
                    landingPos   = lp
                    landingAlpha = if (lp != null) 0.55f else 0f
                }

                // ── Saiu do campo (topo/fundo sem ressalto) ──────────────
                // Apenas para bolas que saem em voo pelo topo ou fundo extremo.
                // Saída pelos lados e linhas de fundo é verificada no ressalto.
                if (ballPos.y < 60f || ballPos.y > VH - 60f) {
                    referee.saySimple("Out")
                    // Topo (y<60) = campo da CPU → jogador mandou além → CPU ganha (1)
                    // Fundo (y>VH-60) = campo do jogador → CPU mandou além → jogador ganha (2)
                    val outWinner = if (ballPos.y < NET_Y) 1 else 2
                    ballVel  = Offset(0f, 0f)
                    ballZVel = 0f
                    awardPoint(outWinner)
                    continue
                }

                // ── Bateu na rede ─────────────────────────────────────────────
                // Serviço: bola na rede = falta (independente de passar ou não)
                // Rally: bola na rede e NÃO passa (z≤0 ao cruzar) = ponto ao adversário
                //        bola na rede e PASSA (z>0) = jogada prossegue normalmente
                if (abs(ballPos.y - NET_Y) < 14f && ballZ <= 0f) {
                    onSound?.invoke("net")
                    if (serveInProgress) {
                        // Serviço na rede → sempre falta
                        serveInProgress = false
                        serveFault++
                        if (serveFault >= 2) {
                            serveFault = 0
                            scope.launch {
                                msg = "DUPLA FALTA!"
                                referee.saySimple("Double fault")
                                delay(1500); msg = ""
                                // Dupla falta → ponto do receptor
                                awardPoint(if (serverPlayer == 1) 2 else 1)
                            }
                        } else {
                            doFault(inNet = true)
                        }
                    } else {
                        // Rally: bola presa na rede → quem enviou perde
                        // ballVel.y < 0 → ia para campo CPU → jogador enviou → CPU ganha (1)
                        // ballVel.y > 0 → ia para campo jogador → CPU enviou → jogador ganha (2)
                        val netWinner = if (ballVel.y < 0f) 1 else 2
                        ballVel  = Offset(0f, 0f)
                        ballZVel = 0f
                        awardPoint(netWinner)
                    }
                    continue
                }

                // Ressalto no chão
                if (ballZ <= 0f && ballZVel < 0f) {

                    // Verificar se caiu fora das linhas do campo:
                    // x in 100..900, y in 100..1500 (bater na linha = dentro)
                    val outOfBounds =
                        ballPos.x < 100f || ballPos.x > 900f ||
                        ballPos.y < 100f || ballPos.y > 1500f
                    if (outOfBounds) {
                        referee.saySimple("Out")
                        // A posição define em que campo caiu:
                        // campo CPU (y<NET_Y) → jogador mandou fora → CPU ganha (1)
                        // campo jogador (y>NET_Y) → CPU mandou fora → jogador ganha (2)
                        val outWinner = if (ballPos.y < NET_Y) 1 else 2
                        ballVel  = Offset(0f, 0f)
                        ballZVel = 0f
                        ballZ    = 0f
                        awardPoint(outWinner)
                        continue
                    }

                    onSound?.invoke("bounce")

                    val side =
                        if (ballPos.y < NET_Y) "cpu" else "player"

                    if (side == lastBounceSide) {
                        bounceCount++
                    } else {
                        bounceCount    = 1
                        lastBounceSide = side
                    }

                    // Notifica a AI quando bola ressalta no campo da CPU
                    if (side == "cpu") {
                        ai?.onBounceCpuSide()
                    }

                    // Validação do serviço (1º ressalto)
                    if (serveInProgress && bounceCount == 1) {
                        val inBox = isServeInBox(
                            ballPos, serverPlayer, serveFromRight
                        )
                        if (!inBox) {
                            serveFault++
                            if (serveFault >= 2) {
                                serveFault = 0
                                scope.launch {
                                    msg = "DUPLA FALTA!"
                                    referee.saySimple("Double fault")
                                    delay(1500); msg = ""
                                    awardPoint(
                                        if (serverPlayer == 1) 2 else 1
                                    )
                                }
                            } else {
                                doFault()
                            }
                            continue
                        } else {
                            serveFault      = 0
                            serveInProgress = false
                        }
                    }

                    // 2 ressaltos no mesmo campo → perde o dono desse campo
                    // Campo "cpu" (y<NET_Y): perde a CPU → winner=jogador=2... espera:
                    // awardPoint(1) = ponto da CPU, awardPoint(2) = ponto do jogador
                    // Se bola bate 2x no campo cpu → jogador ganha → awardPoint(2)
                    // Se bola bate 2x no campo player → cpu ganha → awardPoint(1)
                    if (bounceCount >= 2) {
                        val winner =
                            if (lastBounceSide == "cpu") 2 else 1
                        // Para a bola imediatamente para evitar que saia
                        // do campo e dispare um segundo awardPoint antes
                        // de pointInProgress ser activado pela coroutine.
                        ballVel  = Offset(0f, 0f)
                        ballZVel = 0f
                        ballZ    = 0f
                        awardPoint(winner)
                        continue
                    }
                }

                // ── Movimento e hit da CPU ─────────────────────────────────
                val ballToCpu = ballVel.y < 0f

                p2X = ai?.update(
                    p2X, p2Y, ballPos, ballVel,
                    ballZ, ballZVel, c.gravity, ballToCpu
                ) ?: if (ballToCpu) {
                    (p2X + (ballPos.x - p2X)
                        .coerceIn(-c.cpuSpeed, c.cpuSpeed))
                        .coerceIn(80f, VW - 80f)
                } else {
                    p2X
                }
                p2RacketDir =
                    if (ballPos.x > p2X) 1f else -1f

                // CPU só bate se:
                //   1. Não está bloqueada por LOB (cpuLobBlocked)
                //   2. ballZ < CPU_MAX_Z
                //   3. Bola está no seu range de hit
                val cpuBlocked = ai?.cpuLobBlocked ?: false

                if (!cpuBlocked
                    && lastHitter != 2
                    && abs(ballPos.x - p2X) < c.cpuHitRange
                    && ballPos.y in 80f..430f
                    && ballZ < CPU_MAX_Z
                ) {
                    val (shotTarget, cpuShot) = ai?.chooseShotTarget(p1X)
                        ?: Pair(
                            if (p1X > 500f)
                                150f + kotlin.random.Random.nextFloat() * 250f
                            else
                                600f + kotlin.random.Random.nextFloat() * 250f,
                            ShotType.NORMAL
                        )
                    lastHitter     = 2
                    ballAlreadyHit = false
                    bounceCount    = 0
                    lastBounceSide = "player"
                    onSound?.invoke("hit")
                    p2AnimStep = 3
                    val (vel, zv) = computeCpuHit(
                        p2X, shotTarget, cpuShot, c.speed
                    )
                    ballVel  = vel
                    ballZVel = zv
                    if (ai != null) {
                        ai.lastPlayerShot = ShotType.NORMAL
                        ai.cpuLobBlocked  = false
                    }
                    scope.launch { delay(300); p2AnimStep = 0 }
                }

                // ── Hit do Jogador ─────────────────────────────────────────
                if (!ballAlreadyHit
                    && lastHitter != 1
                    && abs(ballPos.x - p1X) < c.playerHitMargin
                    && ballPos.y in (p1Y - 130f)..(p1Y + 100f)
                    && ballZ < 120f
                ) {
                    ballAlreadyHit = true
                    lastHitter     = 1
                    animStep       = 3
                    bounceCount    = 0
                    lastBounceSide = "cpu"
                    p1RacketDir    =
                        if (ballPos.x > p1X) 1f else -1f
                    onSound?.invoke("hit")

                    val shotToApply = pendingShot ?: ShotType.NORMAL
                    val (vel, zv)   = computePlayerHit(
                        ballPos, p1X, shotToApply, c.speed
                    )
                    ballVel  = vel
                    ballZVel = zv

                    if (ai != null) {
                        ai.lastPlayerShot = shotToApply
                        // LOB: bloqueia CPU de bater até 1º ressalto no seu campo
                        ai.cpuLobBlocked  = (shotToApply == ShotType.LOB)
                    }

                    pendingShot  = null
                    landingPos   = null
                    landingAlpha = 0f
                    scope.launch { delay(300); animStep = 0 }
                }
            }
        }
    }

    LaunchedEffect(screenState) {
        if (screenState == 1) focusRequester.requestFocus()
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown) {
                    val s = when (it.key) {
                        Key.W        -> ShotType.LOB
                        Key.S        -> ShotType.AMORTIE
                        Key.A        -> ShotType.LEFT
                        Key.D        -> ShotType.RIGHT
                        Key.Spacebar -> ShotType.NORMAL
                        else         -> null
                    }
                    if (s != null) { pendingShot = s; true } else false
                } else false
            }
    ) {
        when (screenState) {
            0 -> MenuScreen { diff ->
                difficulty       = diff
                screenState      = 1
                p1Sets = 0;  p2Sets = 0
                p1Games = 0; p2Games = 0
                p1Pts = 0;   p2Pts = 0
                gamesPlayedInSet = 0
                serverPlayer     = 1
                serveFromRight   = true
                serveFault       = 0
                resetForServer()
            }
            2 -> EndScreen(p1Sets, p2Sets) {
                difficulty  = null
                screenState = 0
            }
            else -> GamePlayScreen(
                p1Sets, p2Sets, p1Games, p2Games, p1Pts, p2Pts,
                p1X, p1Y, p2X, p2Y,
                p1RacketDir, p2RacketDir,
                ballPos, ballZ,
                animStep, p2AnimStep,
                serving, serverPlayer, serveFromRight,
                msg, servePosMsg, pendingShot,
                landingPos, landingAlpha,
                pointInProgress = pointInProgress,
                onTap = { tapGameX, tapGameY ->
                    if (serving && serverPlayer == 1) {
                        val correctRange = p1ServeXRange(serveFromRight)
                        if (p1X !in correctRange) {
                            val sidePT =
                                if (serveFromRight) "direita" else "esquerda"
                            val sideEN =
                                if (serveFromRight) "right" else "left"
                            servePosMsg =
                                "⚠ Mova-se para a $sidePT para servir"
                            referee.saySimple(
                                "Player, please move to the $sideEN to serve"
                            )
                            scope.launch {
                                delay(2500); servePosMsg = ""
                            }
                            return@GamePlayScreen
                        }
                        val c = cfg ?: return@GamePlayScreen
                        servePosMsg     = ""
                        serving         = false
                        serveInProgress = true
                        bounceCount     = 0
                        lastBounceSide  = "cpu"
                        val targetX = if (p1X > 500f) 250f else 750f
                        val vy      = -20f * c.speed
                        val frames  = (625f - p1Y) / vy
                        ballVel  = Offset((targetX - p1X) / frames, vy)
                        ballZVel = c.gravity * frames * 0.44f
                        lastHitter = 1
                    } else if (!serving && !pointInProgress) {
                        // Rally: classificar shot pela zona do toque
                        pendingShot = classifyTap(tapGameX, tapGameY, p1X, p1Y)
                    }
                },
                onDrag = { dx, dy ->
                    p1X = (p1X + dx * touchSensitivity)
                        .coerceIn(80f, VW - 80f)
                    p1Y = (p1Y + dy * touchSensitivity)
                        .coerceIn(900f, VH - 40f)
                    if (serving && serverPlayer == 1) {
                        ballPos = Offset(p1X, p1Y - 30f)
                        if (p1X in p1ServeXRange(serveFromRight)
                            && servePosMsg.isNotEmpty()
                        ) servePosMsg = ""
                    }
                }
            )
        }
    }
}

// =============================================================================
@Composable
private fun MenuScreen(onDifficultySelected: (Difficulty) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(COLOR_HUD_BG),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("🎾", fontSize = 48.sp)
            Text(
                "MATCH POINT",
                color      = Color(0xFFFFD54F),
                fontSize   = 30.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "TENNIS",
                color         = Color(0xFF90CAF9),
                fontSize      = 20.sp,
                fontFamily    = FontFamily.Monospace,
                letterSpacing = 8.sp
            )
            Spacer(Modifier.height(16.dp))
            listOf(
                Difficulty.BEGINNER     to Color(0xFF2E7D32),
                Difficulty.INTERMEDIATE to Color(0xFFE65100),
                Difficulty.PRO          to Color(0xFFB71C1C)
            ).forEach { (diff, color) ->
                Surface(
                    shape    = RoundedCornerShape(10.dp),
                    color    = color,
                    modifier = Modifier
                        .width(220.dp)
                        .clickable { onDifficultySelected(diff) }
                ) {
                    Text(
                        difficultyConfigs[diff]!!.label,
                        Modifier.padding(
                            horizontal = 24.dp,
                            vertical   = 14.dp
                        ),
                        color      = Color.White,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign  = TextAlign.Center
                    )
                }
            }
        }
    }
}

// =============================================================================
@Composable
private fun EndScreen(
    p1Sets: Int,
    p2Sets: Int,
    onMenu: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(COLOR_HUD_BG),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🏆", fontSize = 56.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                if (p2Sets > p1Sets) "JOGADOR VENCEU!" else "CPU VENCEU!",
                color      = Color(0xFFFFD54F),
                fontSize   = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "$p2Sets – $p1Sets SETS",
                color      = Color.White,
                fontSize   = 18.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onMenu) { Text("MENU PRINCIPAL") }
        }
    }
}

// =============================================================================
@Composable
private fun GamePlayScreen(
    p1Sets:  Int, p2Sets:  Int,
    p1Games: Int, p2Games: Int,
    p1Pts:   Int, p2Pts:   Int,
    p1X: Float, p1Y: Float,
    p2X: Float, p2Y: Float,
    p1RacketDir:   Float,
    p2RacketDir:   Float,
    ballPos:       Offset,
    ballZ:         Float,
    animStep:      Int,
    p2AnimStep:    Int,
    serving:       Boolean,
    serverPlayer:  Int,
    serveFromRight: Boolean,
    msg:          String,
    servePosMsg:  String,
    pendingShot:  ShotType?,
    landingPos:   Offset?,
    landingAlpha: Float,
    pointInProgress: Boolean,
    onTap:  (tapGameX: Float, tapGameY: Float) -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    var canvasSX by remember { mutableStateOf(1f) }
    var canvasSY by remember { mutableStateOf(1f) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ── HUD ──────────────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(COLOR_HUD_BG)
                .padding(horizontal = 12.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "JOGADOR",
                    color      = Color(0xFFEF9A9A),
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$p1Sets  $p1Games",
                    color      = Color(0xFFEF9A9A),
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "sets  jogos",
                    color    = Color(0xFF616161),
                    fontSize = 8.sp
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val scoreDisplay = if (p1Pts >= 3 && p2Pts >= 3) {
                    when {
                        p1Pts == p2Pts -> "DEUCE"
                        p1Pts  > p2Pts -> "VAN. JOG"
                        else           -> "VAN. CPU"
                    }
                } else {
                    "${tennisLabel(p1Pts)} - ${tennisLabel(p2Pts)}"
                }
                Text(
                    scoreDisplay,
                    color      = Color(0xFFFFD54F),
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    if (serverPlayer == 1) "▲ SERVE" else "▼ SERVE",
                    color    = if (serverPlayer == 1)
                                   Color(0xFF90CAF9)
                               else
                                   Color(0xFFEF9A9A),
                    fontSize = 9.sp
                )
                if (pendingShot != null) {
                    Text(
                        "$pendingShot",
                        color      = Color.Cyan,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "CPU",
                    color      = Color(0xFF90CAF9),
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$p2Sets  $p2Games",
                    color      = Color(0xFF90CAF9),
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "sets  jogos",
                    color    = Color(0xFF616161),
                    fontSize = 8.sp
                )
            }
        }

        // ── Mensagem ─────────────────────────────────────────────────────────
        if (msg.isNotEmpty() || servePosMsg.isNotEmpty()) {
            val display = if (msg.isNotEmpty()) msg else servePosMsg
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .padding(vertical = 10.dp),
                Alignment.Center
            ) {
                Text(
                    display,
                    color      = Color(0xFFFFD54F),
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // ── Campo ─────────────────────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val gx = offset.x / canvasSX
                        val gy = offset.y / canvasSY
                        onTap(gx, gy)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onDrag(
                            drag.x / (size.width  / VW),
                            drag.y / (size.height / VH)
                        )
                    }
                }
        ) {
            val sX = size.width  / VW
            val sY = size.height / VH
            canvasSX = sX
            canvasSY = sY

            drawRect(COLOR_COURT_BG, size = size)

            val lw = 2f * sX
            drawLine(
                COLOR_COURT_LINE,
                Offset(100f * sX, 0f),
                Offset(100f * sX, size.height), lw
            )
            drawLine(
                COLOR_COURT_LINE,
                Offset(900f * sX, 0f),
                Offset(900f * sX, size.height), lw
            )
            drawLine(
                COLOR_COURT_LINE,
                Offset(100f * sX, 100f  * sY),
                Offset(900f * sX, 100f  * sY), lw
            )
            drawLine(
                COLOR_COURT_LINE,
                Offset(100f * sX, 1500f * sY),
                Offset(900f * sX, 1500f * sY), lw
            )
            drawLine(
                COLOR_COURT_LINE,
                Offset(100f * sX, 450f * sY),
                Offset(900f * sX, 450f * sY), lw
            )
            drawLine(
                COLOR_COURT_LINE,
                Offset(100f * sX, 1150f * sY),
                Offset(900f * sX, 1150f * sY), lw
            )
            drawLine(
                COLOR_COURT_LINE,
                Offset(500f * sX, 100f  * sY),
                Offset(500f * sX, 1500f * sY),
                lw * 0.7f
            )

            // Quadrado de serviço
            if (serving && serverPlayer == 1) {
                val boxX = if (serveFromRight) 100f else 500f
                drawRect(
                    Color(0x3300FF88),
                    Offset(boxX * sX, 450f * sY),
                    Size(400f * sX, 350f * sY)
                )
                drawRect(
                    Color(0x9900FF88),
                    Offset(boxX * sX, 450f * sY),
                    Size(400f * sX, 350f * sY),
                    style = Stroke(2.5f * sX)
                )
            }

            // Rede
            drawLine(
                COLOR_NET,
                Offset(0f, NET_Y * sY),
                Offset(size.width, NET_Y * sY),
                4f * sX
            )

            // Marcador de queda
            landingPos?.let { lp ->
                drawBallLandingMarker(lp.x, lp.y, sX, sY, landingAlpha)
            }


            // ── Zonas de shot (rally) ─────────────────────────────────
            if (!serving && !pointInProgress) {
                // LOB — faixa junto à rede (azul claro)
                drawRect(Color(0x1A00CFFF), Offset(100f*sX, NET_Y*sY),      Size(800f*sX, 200f*sY))
                drawRect(Color(0x4400CFFF), Offset(100f*sX, NET_Y*sY),      Size(800f*sX, 200f*sY), style = Stroke(1.5f*sX))
                // AMORTIE — faixa na linha de fundo (laranja)
                drawRect(Color(0x1AFF9800), Offset(100f*sX, 1320f*sY),      Size(800f*sX, 180f*sY))
                drawRect(Color(0x44FF9800), Offset(100f*sX, 1320f*sY),      Size(800f*sX, 180f*sY), style = Stroke(1.5f*sX))
                // ESQUERDA — faixa lateral esquerda (verde)
                drawRect(Color(0x1A00E676), Offset(100f*sX, (NET_Y+200f)*sY), Size(180f*sX, 920f*sY))
                drawRect(Color(0x4400E676), Offset(100f*sX, (NET_Y+200f)*sY), Size(180f*sX, 920f*sY), style = Stroke(1.5f*sX))
                // DIREITA — faixa lateral direita (magenta)
                drawRect(Color(0x1AE040FB), Offset(720f*sX, (NET_Y+200f)*sY), Size(180f*sX, 920f*sY))
                drawRect(Color(0x44E040FB), Offset(720f*sX, (NET_Y+200f)*sY), Size(180f*sX, 920f*sY), style = Stroke(1.5f*sX))
            }

            // ── Highlight do shot pendente ─────────────────────────────
            when (pendingShot) {
                ShotType.LOB     -> drawRect(Color(0x6600CFFF), Offset(100f*sX, NET_Y*sY),        Size(800f*sX, 200f*sY))
                ShotType.AMORTIE -> drawRect(Color(0x66FF9800), Offset(100f*sX, 1320f*sY),        Size(800f*sX, 180f*sY))
                ShotType.LEFT    -> drawRect(Color(0x6600E676), Offset(100f*sX, (NET_Y+200f)*sY), Size(180f*sX, 920f*sY))
                ShotType.RIGHT   -> drawRect(Color(0x66E040FB), Offset(720f*sX, (NET_Y+200f)*sY), Size(180f*sX, 920f*sY))
                else             -> {}
            }

            // Jogadores
            drawMatchPointPlayer(
                p2X * sX, p2Y * sY, 0.9f,
                p2AnimStep, sX, sY,
                p2RacketDir, COLOR_PLAYER2
            )
            drawMatchPointPlayer(
                p1X * sX, p1Y * sY, 1.0f,
                animStep, sX, sY,
                p1RacketDir, COLOR_PLAYER1
            )

            // Sombra da bola no chão
            drawCircle(
                Color(0x44000000),
                7f * sX,
                Offset(ballPos.x * sX, ballPos.y * sY)
            )
            // Bola com arco visual
            val ballScreenY = ballPos.y * sY - ballZ * sY * 0.6f
            drawCircle(
                COLOR_BALL,
                8f * sX,
                Offset(ballPos.x * sX, ballScreenY)
            )
        }
    }
}
