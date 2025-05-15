package org.luma.lumacompanion3

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import org.json.JSONObject

class ElevenLabsTTSClient(private val context: Context) {
    private val client = OkHttpClient()
    private var exoPlayer: ExoPlayer? = null
    private val apiKey = BuildConfig.ELEVENLABS_API_KEY
    private val voiceId = BuildConfig.ELEVENLABS_VOICE_ID

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(context).build()
    }

    suspend fun speak(text: String, onPlaybackComplete: (() -> Unit)? = null) {
        try {
            val audioFile = downloadAudio(text)
            Log.d("ElevenLabsTTS", "Audio file ready, scheduling playback.")
            withContext(Dispatchers.Main) {
                playFile(audioFile, onPlaybackComplete)
            }
        } catch (e: Exception) {
            Log.e("ElevenLabsTTS", "Error playing audio", e)
            onPlaybackComplete?.invoke()
        }
    }

    private suspend fun downloadAudio(text: String): File = withContext(Dispatchers.IO) {
        val url = "https://api.elevenlabs.io/v1/text-to-speech/$voiceId/stream"
        val json = JSONObject().apply {
            put("text", text)
            put("model_id", "eleven_multilingual_v2")
            put("voice_settings", JSONObject().apply {
                put("stability", 0.5)
                put("similarity_boost", 0.8)
            })
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("xi-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e("ElevenLabsTTS", "TTS API error body: ${response.body?.string()}")
            throw Exception("Failed to get audio: ${response.code}")
        }

        val audioFile = File(context.cacheDir, "eleven_tts_${System.currentTimeMillis()}.mp3")
        response.body?.byteStream()?.use { inputStream ->
            FileOutputStream(audioFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
                outputStream.fd.sync()
            }
        }
        audioFile
    }

    private fun playFile(file: File, onPlaybackComplete: (() -> Unit)? = null) {
        exoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
            val fileUri = "file://${file.absolutePath}"
            Log.d("ElevenLabsTTS", "Starting playback: $fileUri")
            player.setMediaItem(MediaItem.fromUri(fileUri))
            player.prepare()
            player.play()
            if (onPlaybackComplete != null) {
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            player.removeListener(this)
                            onPlaybackComplete()
                        }
                    }
                })
            }
        } ?: onPlaybackComplete?.invoke()
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
} 