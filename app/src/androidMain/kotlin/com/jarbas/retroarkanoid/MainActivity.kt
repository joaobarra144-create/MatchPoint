package com.jarbas.retroarkanoid

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Build
import android.speech.tts.TextToSpeech
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale

// ── Text-to-Speech ────────────────────────────────────────────────────────────
class AndroidSpeaker(context: Context) : ScoreSpeaker, TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.UK
            ready = true
        }
    }

    override fun speak(text: String) {
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}

// ── Sons de jogo via SoundPool ────────────────────────────────────────────────
class GameSoundPlayer(context: Context) {
    private val soundPool: SoundPool
    private var sndHit = 0
    private var sndBounce = 0
    private var sndNet = 0

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()

        sndHit    = loadTone(context, 880f, 0.06f, "hit.wav")
        sndBounce = loadTone(context, 440f, 0.08f, "bounce.wav")
        sndNet    = loadTone(context, 220f, 0.10f, "net.wav")
    }

    private fun loadTone(context: Context, freq: Float, duration: Float, name: String): Int {
        val sampleRate = 44100
        val numSamples = (sampleRate * duration).toInt()
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = if (i < numSamples * 0.1)
                i.toDouble() / (numSamples * 0.1)
            else
                1.0 - (i - numSamples * 0.1) / (numSamples * 0.9)
            pcm[i] = (envelope * Short.MAX_VALUE * Math.sin(2.0 * Math.PI * freq * t)).toInt().toShort()
        }
        val file = java.io.File(context.cacheDir, name)
        writeWav(file, pcm, sampleRate)
        return soundPool.load(file.absolutePath, 1)
    }

    private fun writeWav(file: java.io.File, pcm: ShortArray, sampleRate: Int) {
        val dataSize = pcm.size * 2
        java.io.DataOutputStream(
            java.io.BufferedOutputStream(java.io.FileOutputStream(file))
        ).use { out ->
            out.writeBytes("RIFF")
            writeInt32LE(out, 36 + dataSize)
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            writeInt32LE(out, 16)
            writeInt16LE(out, 1)
            writeInt16LE(out, 1)
            writeInt32LE(out, sampleRate)
            writeInt32LE(out, sampleRate * 2)
            writeInt16LE(out, 2)
            writeInt16LE(out, 16)
            out.writeBytes("data")
            writeInt32LE(out, dataSize)
            for (s in pcm) writeInt16LE(out, s.toInt())
        }
    }

    private fun writeInt32LE(out: java.io.DataOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }

    private fun writeInt16LE(out: java.io.DataOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    fun play(event: String) {
        val id = when (event) {
            "hit"    -> sndHit
            "bounce" -> sndBounce
            "net"    -> sndNet
            else     -> return
        }
        if (id != 0) soundPool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() { soundPool.release() }
}

// ── Activity ──────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    private var speaker: AndroidSpeaker? = null
    private var sounds: GameSoundPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Usa WindowCompat (androidx) — compatível com API 21+, nunca crasha
        WindowCompat.setDecorFitsSystemWindows(window, false)

        speaker = AndroidSpeaker(this)
        sounds  = GameSoundPlayer(this)

        setContent {
            MatchPointTennis(
                speaker          = speaker,
                onSound          = { event -> sounds?.play(event) },
                touchSensitivity = 1.0f
            )
        }
    }

    // Esconde as barras quando a janela tem foco (inclui após swipe temporário)
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        val decorView = window.decorView
        WindowInsetsControllerCompat(window, decorView).let { ctrl ->
            ctrl.hide(
                WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.navigationBars()
            )
            ctrl.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speaker?.shutdown()
        sounds?.release()
    }
}
