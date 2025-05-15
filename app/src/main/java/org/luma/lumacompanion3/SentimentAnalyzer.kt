package org.luma.lumacompanion3

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class ModerationResult(
    val flagged: Boolean,
    val selfHarmScore: Double,
    val violenceScore: Double,
    val hateScore: Double,
    val harassmentScore: Double,
    val overallSentiment: String
)

suspend fun analyzeSentimentWithOpenAI(text: String, apiKey: String): ModerationResult? = withContext(Dispatchers.IO) {
    val client = OkHttpClient()

    val requestBody = JSONObject().apply {
        put("input", text)
    }.toString().toRequestBody("application/json".toMediaTypeOrNull())

    val request = Request.Builder()
        .url("https://api.openai.com/v1/moderations")
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("Content-Type", "application/json")
        .post(requestBody)
        .build()

    try {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e("LumaSentiment", "OpenAI Moderation failed: HTTP ${response.code}")
            return@withContext null
        }

        val json = JSONObject(response.body?.string())
        val result = json.getJSONArray("results").getJSONObject(0)
        val flagged = result.getBoolean("flagged")
        val scores = result.getJSONObject("category_scores")

        val selfHarm = scores.getDouble("self-harm")
        val violence = scores.getDouble("violence")
        val hate = scores.getDouble("hate")
        val harassment = scores.getDouble("harassment")

        val sentiment = when {
            selfHarm > 0.5 || violence > 0.5 -> "Negative"
            flagged -> "Alert"
            else -> "Neutral"
        }

        ModerationResult(
            flagged = flagged,
            selfHarmScore = selfHarm,
            violenceScore = violence,
            hateScore = hate,
            harassmentScore = harassment,
            overallSentiment = sentiment
        )
    } catch (e: Exception) {
        Log.e("LumaSentiment", "Error during sentiment analysis: ${e.message}")
        null
    }
} 