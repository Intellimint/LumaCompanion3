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
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.BaseDataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.InputStream
import org.json.JSONObject
import androidx.media3.common.C

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
        Log.d("OpenAITTSClient", "TTS request text: '$text'")
        val json = JSONObject().apply {
            put("model", "tts-1")
            put("voice", "shimmer")
            put("input", text)
            put("response_format", "mp3")
        }
        val jsonBody = json.toString()
        Log.d("OpenAITTSClient", "TTS request body: $jsonBody")

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/speech")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        Log.d("OpenAITTSClient", "TTS response code: ${response.code}, message: ${response.message}")
        if (!response.isSuccessful) {
            Log.e("OpenAITTSClient", "TTS API error body: ${response.body?.string()}")
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

    /**
     * Streams audio from OpenAI's TTS API and plays it in real time using ExoPlayer.
     * Uses shimmer voice and opus_stream format for true streaming.
     */
    fun playStreamingShimmerVoice(text: String) {
        val ttsRequest = JSONObject().apply {
            put("model", "tts-1")
            put("voice", "shimmer")
            put("input", text)
            put("response_format", "opus_stream")
        }.toString()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/speech")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(ttsRequest.toRequestBody("application/json".toMediaType()))
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                val inputStream = response.body?.byteStream()
                if (inputStream != null) {
                    playOpusStream(inputStream)
                } else {
                    Log.e("OpenAITTSClient", "Streaming TTS: InputStream is null")
                }
            } catch (e: Exception) {
                Log.e("OpenAITTSClient", "Streaming TTS error", e)
            }
        }
    }

    /**
     * Plays an Opus audio stream using ExoPlayer and a custom DataSource.
     */
    private fun playOpusStream(inputStream: InputStream) {
        val dataSourceFactory = DataSource.Factory {
            object : BaseDataSource(true) {
                override fun open(dataSpec: DataSpec): Long = C.LENGTH_UNSET.toLong()
                override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int = inputStream.read(buffer, offset, readLength)
                override fun getUri(): Uri? = null
                override fun close() = inputStream.close()
            }
        }
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri("http://localhost/stream.opus"))
        CoroutineScope(Dispatchers.Main).launch {
            exoPlayer?.apply {
                stop()
                clearMediaItems()
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }
        }
    }

    /**
     * Plays TTS audio and invokes a callback with the ExoPlayer instance when ready.
     * This allows the caller to access the duration and schedule actions before playback ends.
     */
    suspend fun playShimmerVoiceWithDurationCallback(text: String, onPlayerReady: (ExoPlayer) -> Unit) {
        try {
            val audioFile = downloadAudioFull(text)
            Log.d("OpenAITTSClient", "Audio file ready, scheduling playback with duration callback.")
            withContext(Dispatchers.Main) {
                exoPlayer?.let { player ->
                    player.stop()
                    player.clearMediaItems()
                    val fileUri = "file://${audioFile.absolutePath}"
                    lastAudioFile = audioFile
                    player.setMediaItem(MediaItem.fromUri(fileUri))
                    player.prepare()
                    // Add a one-time listener for STATE_READY
                    val listener = object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) {
                                player.removeListener(this)
                                onPlayerReady(player)
                                player.play()
                            }
                        }
                    }
                    player.addListener(listener)
                }
            }
        } catch (e: Exception) {
            Log.e("OpenAITTSClient", "Error playing audio with duration callback", e)
        }
    }
} 