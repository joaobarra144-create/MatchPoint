package com.jarbas.retroarkanoid

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow

@JsName("speak")
external fun jsSpeech(text: String)

@JsName("playSound")
external fun jsPlaySound(name: String)

class WebScoreSpeaker : ScoreSpeaker {
    override fun speak(text: String) {
        try { jsSpeech(text) } catch (e: Throwable) {}
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow("Match Point Tennis", canvasElementId = "ComposeTarget") {
        MatchPointTennis(
            speaker          = WebScoreSpeaker(),
            onSound          = { name -> try { jsPlaySound(name) } catch (e: Throwable) {} },
            touchSensitivity = 1.8f,
        //    uiScale          = 1.0f
        )
    }
}
