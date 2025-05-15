package org.luma.lumacompanion3

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConversationLogger {
    private const val LOG_FILE_NAME = "conversation_log.jsonl"

    fun saveEntry(
        context: Context,
        userInput: String,
        aiResponse: String,
        sentiment: String,
        flags: ModerationResult
    ) {
        try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
            val json = JSONObject().apply {
                put("timestamp", timestamp)
                put("userInput", userInput)
                put("aiResponse", aiResponse)
                put("sentiment", sentiment)
                put("flagged", flags.flagged)
                put("selfHarmScore", flags.selfHarmScore)
                put("violenceScore", flags.violenceScore)
                put("hateScore", flags.hateScore)
                put("harassmentScore", flags.harassmentScore)
                put("overallSentiment", flags.overallSentiment)
            }
            logFile.appendText(json.toString() + "\n")
            Log.d("ConversationLogger", "Logged entry: $json")
        } catch (e: Exception) {
            Log.e("ConversationLogger", "Failed to log entry: ${e.message}")
        }
    }
} 