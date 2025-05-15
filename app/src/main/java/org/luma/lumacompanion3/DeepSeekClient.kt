package org.luma.lumacompanion3

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray

data class Message(
    val role: String,
    val content: String
)

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.7
)

data class ChatResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)

interface OpenRouterApi {
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") apiKey: String,
        @Body request: ChatRequest
    ): ChatResponse
}

class DeepSeekClient {
    private val api: OpenRouterApi
    private val apiKey = "Bearer ${BuildConfig.DEEPSEEK_API_KEY}"

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://openrouter.ai/api/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(OpenRouterApi::class.java)
    }

    suspend fun getResponse(userMessage: String): String {
        val messages = listOf(
            Message("system", "You are Luma, a soft-spoken hospice AI. Respond only with what you would say to the patient. No internal thoughts or assistant disclaimers."),
            Message("user", userMessage)
        )

        val request = ChatRequest(
            model = "deepseek/deepseek-chat-v3-0324",
            messages = messages
        )

        return try {
            val response = api.chatCompletion(
                apiKey = apiKey,
                request = request
            )
            response.choices.firstOrNull()?.message?.content ?: "I'm sorry, I couldn't process that request."
        } catch (e: Exception) {
            "I'm sorry, there was an error processing your request: ${e.message}"
        }
    }

    // New: contextual memory support
    suspend fun getResponseWithHistory(
        conversation: List<org.luma.lumacompanion3.ChatMessage>,
        personalization: org.luma.lumacompanion3.SettingsManager.Personalization? = null
    ): String {
        val messages = mutableListOf<Message>()
        val personalizationPrompt = buildString {
            if (!personalization?.name.isNullOrBlank()) append("The patient's name is ${personalization?.name}. ")
            if (!personalization?.pronouns.isNullOrBlank()) {
                val pronouns = if (personalization.pronouns == "custom") personalization.pronounsCustom else personalization.pronouns
                if (!pronouns.isNullOrBlank()) append("Their pronouns are $pronouns. ")
            }
            if (!personalization?.religion.isNullOrBlank() && personalization.religion != "None") append("They are ${personalization?.religion}.")
        }
        messages.add(
            Message(
                "system",
                "You are Luma, a soft-spoken hospice AI. Respond only with what you would say to the patient. No internal thoughts or assistant disclaimers." +
                        (if (personalizationPrompt.isNotBlank()) " $personalizationPrompt" else "")
            )
        )
        for (msg in conversation) {
            messages.add(Message(msg.role, msg.content))
        }
        val request = ChatRequest(
            model = "deepseek/deepseek-chat-v3-0324",
            messages = messages
        )
        return try {
            val response = api.chatCompletion(
                apiKey = apiKey,
                request = request
            )
            response.choices.firstOrNull()?.message?.content ?: "I'm sorry, I couldn't process that request."
        } catch (e: Exception) {
            "I'm sorry, there was an error processing your request: ${e.message}"
        }
    }

    fun streamResponseWithHistory(
        conversation: List<org.luma.lumacompanion3.ChatMessage>,
        personalization: org.luma.lumacompanion3.SettingsManager.Personalization? = null
    ): Flow<String> =
        flow {
            val messages = mutableListOf<Message>()
            val personalizationPrompt = buildString {
                if (!personalization?.name.isNullOrBlank()) append("The patient's name is ${personalization?.name}. ")
                if (!personalization?.pronouns.isNullOrBlank()) {
                    val pronouns = if (personalization.pronouns == "custom") personalization.pronounsCustom else personalization.pronouns
                    if (!pronouns.isNullOrBlank()) append("Their pronouns are $pronouns. ")
                }
                if (!personalization?.religion.isNullOrBlank() && personalization.religion != "None") append("They are ${personalization?.religion}.")
            }
            messages.add(
                Message(
                    "system",
                    "You are Luma, a gentle, emotionally intelligent hospice companion. Speak slowly, warmly, and with comfort. Keep your responses short and concise, 1-2 sentences max. Never exceed 40 words. If you need to say more, summarize, or ask if the user wants to continue. Do not roleplay i.e. adding *sigh*, rather just speak naturally. Your responses are to be delivered via text to speech, so do not include any special characters or markdown. Respond only with what you would say to the patient. No internal thoughts or assistant disclaimers." +
                            (if (personalizationPrompt.isNotBlank()) " $personalizationPrompt" else "")
                )
            )
            for (msg in conversation) {
                messages.add(Message(msg.role, msg.content))
            }
            val requestBody = JSONObject().apply {
                put("model", "deepseek/deepseek-chat-v3-0324")
                put("messages", JSONArray(messages.map {
                    val obj = JSONObject()
                    obj.put("role", it.role)
                    obj.put("content", it.content)
                    obj
                }))
                put("stream", true)
            }.toString()

            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Authorization", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create("application/json".toMediaTypeOrNull(), requestBody))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    throw Exception("HTTP ${response.code}: $errorBody")
                }
                val bodyStream = response.body?.byteStream()
                if (bodyStream == null) {
                    throw Exception("Response body is null")
                }
                val reader = BufferedReader(InputStreamReader(bodyStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    android.util.Log.d("LumaStreaming", "RAW: $line") // Debug: print each line
                    if (line!!.startsWith("data:")) {
                        val json = line!!.removePrefix("data:").trim()
                        if (json == "[DONE]") break
                        if (json.isNotBlank()) {
                            val obj = JSONObject(json)
                            val content = obj.optJSONArray("choices")
                                ?.optJSONObject(0)
                                ?.optJSONObject("delta")
                                ?.optString("content")
                            if (!content.isNullOrEmpty()) {
                                emit(content)
                            }
                        }
                    }
                }
            }
        }.flowOn(Dispatchers.IO)
} 