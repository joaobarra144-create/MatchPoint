package com.jarbas.retroarkanoid

interface ScoreSpeaker {
    fun speak(text: String)
}

class RefereeManager(private val speaker: ScoreSpeaker? = null) {

    private val labels = listOf("Love", "Fifteen", "Thirty", "Forty")

    // jogPts = pontos do Jogador (p1)
    // cpuPts = pontos da CPU    (p2)
    // serverPlayer: 1 = Jogador serve, 2 = CPU serve
    fun sayScore(jogPts: Int, cpuPts: Int, serverPlayer: Int = 1) {
        speaker?.speak(buildScoreText(jogPts, cpuPts, serverPlayer))
    }

    fun buildScoreText(jogPts: Int, cpuPts: Int, serverPlayer: Int): String {
        val deuce = jogPts >= 3 && cpuPts >= 3
        return when {
            deuce -> when {
                jogPts == cpuPts -> "Deuce"
                jogPts > cpuPts  -> "Advantage C P U"
                else             -> "Advantage Player"
            }
            else -> {
                // Árbitro dita sempre: servidor primeiro, recebedor depois
                val srvPts = if (serverPlayer == 1) jogPts else cpuPts
                val recPts = if (serverPlayer == 1) cpuPts else jogPts
                val srvLabel = labels.getOrElse(srvPts) { "Forty" }
                val recLabel = labels.getOrElse(recPts) { "Forty" }
                if (srvPts == recPts) "$srvLabel All" else "$srvLabel $recLabel"
            }
        }
    }

    // winner: 1=Jogador, 2=CPU
    fun sayGameWinner(serverPlayer: Int, winner: Int) =
        speaker?.speak("Game, ${if (winner == 1) "Player" else "C P U"}")

    fun saySetWinner(winner: Int) =
        speaker?.speak("Set, ${if (winner == 1) "Player" else "C P U"}")

    fun sayMatchWinner(winner: Int) =
        speaker?.speak("Game, Set, and Match, ${if (winner == 1) "Player" else "C P U"}")

    fun saySimple(text: String) = speaker?.speak(text)
}
