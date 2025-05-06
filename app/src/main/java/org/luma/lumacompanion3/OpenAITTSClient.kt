package org.luma.lumacompanion3

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import android.net.Uri

class OpenAITTSClient(private val context: Context) {
    private val client = OkHttpClient()
    private var exoPlayer: ExoPlayer? = null
    private val apiKey = "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"
    private var lastAudioFile: File? = null
    private var retryCount = 0
    private val retryScope = CoroutineScope(Dispatchers.Main)

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e("OpenAITTSClient", "ExoPlayer error: $error. Retrying...")
                    if (retryCount < 1 && lastAudioFile != null) {
                        retryCount++
                        playFileWithDelay(lastAudioFile!!)
                    } else {
                        retryCount = 0
                    }
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        retryCount = 0
                    }
                }
            })
        }
    }

    suspend fun playShimmerVoice(text: String) {
        try {
            val audioFile = downloadAudioFull(text)
            Log.d("OpenAITTSClient", "Audio file ready, scheduling playback.")
            withContext(Dispatchers.Main) {
                playFileWithDelay(audioFile)
            }
        } catch (e: Exception) {
            Log.e("OpenAITTSClient", "Error playing audio", e)
        }
    }

    /**
     * Downloads the full audio from OpenAI's TTS API, then starts playback.
     * This ensures ExoPlayer plays the entire response without cutting off.
     */
    private suspend fun downloadAudioFull(text: String): File = withContext(Dispatchers.IO) {
        val jsonBody = """
            {
                "model": "tts-1",
                "voice": "shimmer",
                "input": "$text",
                "response_format": "mp3",
                "stream": true
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/speech")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Failed to get audio: ${response.code}")
        }

        val audioFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
        response.body?.byteStream()?.use { inputStream ->
            FileOutputStream(audioFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var totalBytes = 0
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                    Log.d("OpenAITTSClient", "Downloaded $totalBytes bytes...")
                }
                // Flush and sync to ensure file is fully written
                outputStream.flush()
                outputStream.fd.sync()
            }
        }
        Log.d("OpenAITTSClient", "Final audio file size: ${audioFile.length()} bytes")
        audioFile
    }

    private fun playFileWithDelay(file: File) {
        Log.d("OpenAITTSClient", "Preparing to play file after delay: ${file.absolutePath}, size: ${file.length()}")
        retryScope.launch {
            delay(200)
            startPlayback(file)
        }
    }

    private fun startPlayback(file: File) {
        exoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
            val fileUri = "file://${file.absolutePath}"
            Log.d("OpenAITTSClient", "Starting playback: $fileUri")
            lastAudioFile = file
            player.setMediaItem(MediaItem.fromUri(fileUri))
            player.prepare()
            player.play()
            Log.d("OpenAITTSClient", "Playback started.")
        }
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
} 