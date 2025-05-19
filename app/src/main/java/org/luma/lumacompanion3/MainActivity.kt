package org.luma.lumacompanion3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import android.view.WindowManager
import android.view.WindowInsetsController
import android.os.Build
import android.view.View
import android.view.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.layout
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.compose.ui.zIndex
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import org.luma.lumacompanion3.ConversationLogger
import org.luma.lumacompanion3.analyzeSentimentWithOpenAI
import java.io.File
import org.json.JSONObject
import android.content.Context

private val Orange = Color(0xFFF26B14)
private val DarkGrey = Color(0xFF18191A)
private val LightGrey = Color(0xFF232526)
private val White = Color(0xFFF5F5F5)

// Message data class for conversation
data class ChatMessage(val role: String, val content: String)

data class WaveData(
    val amplitude: Float,
    val phase: Float,
    val horizontalShift: Float
)

class MainActivity : ComponentActivity() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private var ttsClient: Any? = null // Can be OpenAITTSClient or ElevenLabsTTSClient
    private val deepSeekClient = DeepSeekClient()
    private var isListening = false
    private var persistentListening = false
    private var inactivityJob: Job? = null
    private val inactivityTimeoutMillis = 5 * 60 * 1000L // 5 minutes
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private var isIdleState = false
    private var isSpeaking = false
    private var shouldRestartListening = true
    private var conversation = listOf<ChatMessage>()
    private var isLoading = false
    private var personalization: SettingsManager.Personalization? = null
    private var isListeningOrSpeaking = false
    private val useStreaming = false
    private val wakePhrases = listOf(
        "How are you feeling today?",
        "Is there anything on your mind you'd like to talk about?",
        "How has your day been so far?",
        "Would you like to share something that's been bothering you?",
        "Is there something you're grateful for today?",
        "How are you coping with everything right now?",
        "Is there a memory or thought you'd like to talk about?",
        "What would make today feel a little better for you?",
        "Is there something you wish others understood about how you're feeling?",
        "Would you like to talk about what's been weighing on your heart?"
    )
    var tts: TextToSpeech? = null
    private var selectedVoice: Voice? = null
    private var currentPartialResults = StringBuilder()
    private var lastProcessedText = ""
    private var recognitionActive = false
    private var restartRecognitionJob: Job? = null
    private lateinit var recognitionIntent: Intent
    private var lumaCaption by mutableStateOf("")

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening()
        } else {
            Toast.makeText(this, "Microphone permission is required to use Luma.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            initializeRecognitionIntent()
            ttsClient = if (BuildConfig.TTS_PROVIDER.equals("elevenlabs", ignoreCase = true)) {
                ElevenLabsTTSClient(this)
            } else {
                OpenAITTSClient(this)
            }
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    Log.d("LumaTTS", "TTS initialized successfully")
                    val voices = tts?.voices?.toList() ?: emptyList()
                    val usVoices = voices.filter {
                        it.locale.language == "en" && it.locale.country == "US"
                    }
                    Log.i("LumaTTS", "Available US English voices:")
                    usVoices.forEach { v ->
                        Log.i("LumaTTS", "Voice: ${v.name}, networkRequired: ${v.isNetworkConnectionRequired}, locale: ${v.locale}")
                    }
                    
                    // Try to find en-us-x-tpc-local first
                    selectedVoice = usVoices.find { it.name == "en-us-x-tpc-local" }
                        ?: usVoices.firstOrNull { !it.isNetworkConnectionRequired }
                        ?: usVoices.firstOrNull()
                        
                    if (selectedVoice != null) {
                        tts?.voice = selectedVoice
                        tts?.setSpeechRate(1.2f)
                        Log.i("LumaTTS", "Selected TTS voice: ${selectedVoice!!.name}, networkRequired: ${selectedVoice!!.isNetworkConnectionRequired}, locale: ${selectedVoice!!.locale}")
                    } else {
                        Log.w("LumaTTS", "No suitable US English voice found. Using default.")
                    }
                } else {
                    Log.e("LumaTTS", "TTS initialization failed: status=$status")
                }
            }

            setContent {
                var showSettings by remember { mutableStateOf(false) }
                var isIdle by remember { mutableStateOf(false) }
                var composeIsListeningOrSpeaking by remember { mutableStateOf(false) }
                var showLogViewer by remember { mutableStateOf(false) }
                val context = LocalContext.current

                // Load personalization on launch
                LaunchedEffect(Unit) {
                    personalization = SettingsManager.getPersonalization(context)
                    isIdle = true // Always start in idle/screensaver
                }

                // Kiosk mode setup
                val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val componentName = android.content.ComponentName(context, MyDeviceAdminReceiver::class.java)

                if (devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                    devicePolicyManager.setLockTaskPackages(componentName, arrayOf(context.packageName))
                    // Comment this out for now to avoid locking yourself in during dev
                    // startLockTask()
                }

                val onViewLogs = {
                    Toast.makeText(context, "View Logs pressed", Toast.LENGTH_SHORT).show()
                    showLogViewer = true
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkGrey
                ) {
                    Box(Modifier.fillMaxSize()) {
                        if (isIdle) {
                            ScreensaverUI(
                                onTap = {
                                    isIdle = false
                                    composeIsListeningOrSpeaking = true
                                    persistentListening = true
                                    isListening = false
                                    // Request mic permission before starting listening
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        val wakePhrase = buildWakePhrase()
                                        if (wakePhrase.isNotBlank()) {
                                            playTTSWithCallback(wakePhrase) { startListening() }
                                        } else {
                                            Log.w("Luma", "Wake phrase was blank, skipping TTS playback.")
                                            startListening()
                                        }
                                    } else {
                                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                onViewLogs = onViewLogs
                            )
                        } else if (composeIsListeningOrSpeaking) {
                            VoiceWaveformUI(
                                onClose = {
                                    isIdle = true
                                    composeIsListeningOrSpeaking = false
                                    persistentListening = false
                                    isListening = false
                                    stopContinuousRecognition()
                                    lumaCaption = ""
                                },
                                onViewLogs = onViewLogs
                            )
                        } else {
                            MainScreen(
                                onTapToSpeak = {
                                    isIdle = false
                                    composeIsListeningOrSpeaking = true
                                    persistentListening = true
                                    isListening = false
                                    // Request mic permission before starting listening
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        startPersistentListeningLoop(
                                            context = context,
                                            conversationState = { conversation },
                                            setConversation = { conversation = it },
                                            setIsLoading = { isLoading = it },
                                            setIsIdle = { newIsIdle -> isIdle = newIsIdle },
                                            personalizationState = { personalization },
                                            setIsListeningOrSpeaking = { composeIsListeningOrSpeaking = it }
                                        )
                                        resetInactivityTimer {
                                            mainScope.launch {
                                                persistentListening = false
                                                isIdle = true
                                                val wakePhrase = buildWakePhrase()
                                                if (wakePhrase.isNotBlank()) {
                                                    playTTSWithCallback(wakePhrase) { startListening() }
                                                } else {
                                                    Log.w("Luma", "Wake phrase was blank, skipping TTS playback.")
                                                    startListening()
                                                }
                                            }
                                        }
                                    } else {
                                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                conversation = conversation,
                                isLoading = isLoading,
                                onSettingsClick = { showSettings = true },
                                onClose = {
                                    isIdle = true
                                    composeIsListeningOrSpeaking = false
                                    persistentListening = false
                                    isListening = false
                                    stopContinuousRecognition()
                                    lumaCaption = ""
                                }
                            )
                        }
                        if (showSettings) {
                            SettingsDialog(
                                initial = personalization,
                                onDismiss = { showSettings = false },
                                onSave = { name, pronouns, pronounsCustom, religion ->
                                    mainScope.launch {
                                        SettingsManager.setName(context, name)
                                        SettingsManager.setPronouns(context, pronouns, pronounsCustom)
                                        SettingsManager.setReligion(context, religion)
                                        personalization = SettingsManager.getPersonalization(context)
                                        showSettings = false
                                        // Log personalization change
                                        val pronounsDisplay = if (pronouns == "custom") pronounsCustom ?: "(custom)" else pronouns
                                        val userInput = "Personalization updated: Name set to $name, pronouns set to $pronounsDisplay, religion set to $religion"
                                        ConversationLogger.saveEntry(
                                            context = context,
                                            userInput = userInput,
                                            aiResponse = "(Personalization updated)",
                                            sentiment = "Neutral",
                                            flags = ModerationResult(
                                                flagged = false,
                                                selfHarmScore = 0.0,
                                                violenceScore = 0.0,
                                                hateScore = 0.0,
                                                harassmentScore = 0.0,
                                                overallSentiment = "Neutral"
                                            )
                                        )
                                    }
                                },
                                onViewLogs = onViewLogs
                            )
                        }
                        if (showLogViewer) {
                            LogViewerDialog(onClose = { showLogViewer = false })
                        }
                        // Overlay LumaCaption if lumaCaption is not blank
                        if (lumaCaption.isNotBlank()) {
                            LumaCaption(lumaCaption)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Keep screen on
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // Adjust brightness based on time of day
            adjustBrightnessForTimeOfDay()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let {
                    it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            }
        }
    }

    // Add time-based brightness adjustment
    private fun adjustBrightnessForTimeOfDay() {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val brightness = when (currentHour) {
            in 6..8 -> 0.65f    // Early morning (6AM-8AM): moderate brightness
            in 9..18 -> 0.9f    // Day time (9AM-6PM): high brightness
            in 19..21 -> 0.65f  // Evening (7PM-9PM): moderate brightness
            else -> 0.4f        // Night (10PM-5AM): low brightness
        }
        
        val layoutParams = window.attributes
        layoutParams.screenBrightness = brightness
        window.attributes = layoutParams
    }

    // Estimate tokens as words * 1.3, trim oldest messages if over limit
    private fun trimConversationToTokenLimit(conversation: List<ChatMessage>, maxTokens: Int): List<ChatMessage> {
        fun estimateTokens(msgs: List<ChatMessage>): Int =
            (msgs.sumOf { it.content.split(" ").size } * 1.3).toInt()
        var msgs = conversation
        while (estimateTokens(msgs) > maxTokens && msgs.size > 2) {
            msgs = msgs.drop(1) // drop oldest
        }
        return msgs
    }

    private fun checkPermissionAndStartListening(onResult: (String) -> Unit) {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startListening(onResult)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startListening(onResult: (String) -> Unit = {}) {
        if (isListening) return
        isListening = true

        try {
            startContinuousRecognition { recognizedText ->
                if (recognizedText.isNotBlank()) {
                    onResult(recognizedText)
                    // Don't automatically restart if we're not in persistent mode
                    if (!persistentListening) {
                        stopContinuousRecognition()
                        isListening = false
                    }
                }
            }
        } catch (e: Exception) {
            isListening = false
            Log.e("Luma", "Error starting speech recognition", e)
            if (persistentListening) {
                restartListeningIfNeeded()
            }
        }
    }

    private fun restartListeningIfNeeded() {
        if (!persistentListening || isSpeaking) return
        
        mainScope.launch {
            if (persistentListening && !isListening && !isSpeaking && shouldRestartListening) {
                startPersistentListeningLoop(
                    context = applicationContext,
                    conversationState = { conversation },
                    setConversation = { conversation = it },
                    setIsLoading = { isLoading = it },
                    setIsIdle = { isIdleState = it },
                    personalizationState = { personalization },
                    setIsListeningOrSpeaking = { isListeningOrSpeaking = it }
                )
            }
        }
    }

    // Modify OpenAITTSClient to notify when speaking starts/ends
    private fun playTTSWithCallback(text: String, onComplete: () -> Unit) {
        stopContinuousRecognition() // Always stop listening before TTS
        isSpeaking = true
        shouldRestartListening = false
        lumaCaption = text // Set caption before TTS
        if (text.isBlank()) {
            isSpeaking = false
            shouldRestartListening = true
            isListening = false
            recognitionActive = false
            // lumaCaption = "" // Do NOT clear caption here
            onComplete()
            return
        }
        if (ttsClient is ElevenLabsTTSClient) {
            mainScope.launch {
                (ttsClient as ElevenLabsTTSClient).speak(text) {
                    isSpeaking = false
                    shouldRestartListening = true
                    isListening = false
                    recognitionActive = false
                    // lumaCaption = "" // Do NOT clear caption here
                    onComplete()
                }
            }
        } else if (ttsClient is OpenAITTSClient) {
            mainScope.launch {
                (ttsClient as OpenAITTSClient).playShimmerVoice(text)
                isSpeaking = false
                shouldRestartListening = true
                isListening = false
                recognitionActive = false
                // lumaCaption = "" // Do NOT clear caption here
                onComplete()
            }
        } else {
            // fallback: use Android TTS
            val utteranceId = System.currentTimeMillis().toString()
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("LumaTTS", "TTS onStart: utteranceId=$utteranceId")
                }
                override fun onDone(utteranceId: String?) {
                    Log.d("LumaTTS", "TTS onDone: utteranceId=$utteranceId")
                    runOnUiThread {
                        isSpeaking = false
                        shouldRestartListening = true
                        isListening = false
                        recognitionActive = false
                        // lumaCaption = "" // Do NOT clear caption here
                        onComplete()
                    }
                }
                override fun onError(utteranceId: String?) {
                    Log.e("LumaTTS", "TTS onError: utteranceId=$utteranceId")
                    runOnUiThread {
                        isSpeaking = false
                        shouldRestartListening = true
                        isListening = false
                        recognitionActive = false
                        // lumaCaption = "" // Do NOT clear caption here
                        onComplete()
                    }
                }
            })
            val params = android.os.Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            val speakStatus = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (speakStatus == TextToSpeech.SUCCESS) {
                Log.d("LumaTTS", "TTS speak() called: $text")
            } else {
                Log.e("LumaTTS", "TTS speak() failed: status=$speakStatus, text=$text")
            }
        }
    }

    // Update the persistent listening loop
    private fun startPersistentListeningLoop(
        context: android.content.Context,
        conversationState: () -> List<ChatMessage>,
        setConversation: (List<ChatMessage>) -> Unit,
        setIsLoading: (Boolean) -> Unit,
        setIsIdle: (Boolean) -> Unit,
        personalizationState: () -> SettingsManager.Personalization?,
        setIsListeningOrSpeaking: (Boolean) -> Unit
    ) {
        if (!persistentListening) return
        
        checkPermissionAndStartListening { recognizedText ->
            Log.d("Luma", "Speech recognized: '$recognizedText'")
            showVoiceWaveform()
            mainScope.launch {
                setIsLoading(true)
                resetInactivityTimer {
                    mainScope.launch {
                        persistentListening = false
                        setIsIdle(true)
                        val wakePhrase = buildWakePhrase()
                        if (wakePhrase.isNotBlank()) {
                            playTTSWithCallback(wakePhrase) { startListening() }
                        } else {
                            Log.w("Luma", "Wake phrase was blank, skipping TTS playback.")
                            startListening()
                        }
                    }
                }

                Log.d("Luma", "Processing recognized speech for DeepSeek and TTS: '$recognizedText'")
                val timestamp = getCurrentTimestamp()
                val userMessageForModel = buildUserMessageForModel(recognizedText, personalizationState(), timestamp)
                val updatedConversation = conversationState() + ChatMessage("user", userMessageForModel)
                val updatedConversationForApi = conversationState() + ChatMessage("user", userMessageForModel)
                val trimmedConversation = trimConversationToTokenLimit(updatedConversationForApi, 5000)
                val pers = personalizationState()
                // Sentiment analysis and logging
                val sentimentResult = analyzeSentimentWithOpenAI(recognizedText, BuildConfig.OPENAI_API_KEY)

                if (useStreaming) {
                    // Streaming mode
                    val responseBuilder = StringBuilder()
                    setConversation(updatedConversation) // Show user message immediately
                    try {
                        deepSeekClient.streamResponseWithHistory(trimmedConversation, pers)
                            .collect { token ->
                                responseBuilder.append(token)
                                val newConv = updatedConversation + ChatMessage("assistant", responseBuilder.toString())
                                setConversation(newConv)
                            }
                        setIsLoading(false)
                        val response = responseBuilder.toString()
                        val trimmedResponse = trimResponseForSpeech(response)
                        Log.d("Luma", "DeepSeek streaming response: '$response'")
                        if (sentimentResult != null) {
                            ConversationLogger.saveEntry(
                                context = context,
                                userInput = recognizedText,
                                aiResponse = response,
                                sentiment = sentimentResult.overallSentiment,
                                flags = sentimentResult
                            )
                        }
                        if (trimmedResponse.isNotBlank()) {
                            playTTSWithCallback(trimmedResponse) { startListening() }
                        } else {
                            Log.w("Luma", "TTS response was blank, skipping TTS playback.")
                            startListening()
                        }
                    } catch (e: Exception) {
                        setIsLoading(false)
                        Log.e("LumaStreaming", "Streaming error", e)
                        val errorMsg = "I'm sorry, there was an error processing your request: ${e.message ?: e.javaClass.name}"
                        val newConv = updatedConversation + ChatMessage("assistant", errorMsg)
                        setConversation(newConv)
                    }
                } else {
                    // Old synchronous mode
                    val response = deepSeekClient.getResponseWithHistory(trimmedConversation, pers)
                    val trimmedResponse = trimResponseForSpeech(response)
                    Log.d("Luma", "DeepSeek sync response: '$response'")
                    val newConversation = updatedConversation + ChatMessage("assistant", response)
                    setConversation(newConversation)
                    setIsLoading(false)
                    if (sentimentResult != null) {
                        ConversationLogger.saveEntry(
                            context = context,
                            userInput = recognizedText,
                            aiResponse = response,
                            sentiment = sentimentResult.overallSentiment,
                            flags = sentimentResult
                        )
                    }
                    if (trimmedResponse.isNotBlank()) {
                        playTTSWithCallback(trimmedResponse) { startListening() }
                    } else {
                        Log.w("Luma", "TTS response was blank, skipping TTS playback.")
                        startListening()
                    }
                }
            }
        }
    }

    // Inactivity timer logic
    private fun resetInactivityTimer(onTimeout: () -> Unit) {
        inactivityJob?.cancel()
        inactivityJob = mainScope.launch {
            delay(inactivityTimeoutMillis)
            onTimeout()
        }
    }

    // Returns a timestamp string like: [Time: 3:42 PM | Date: Monday, May 5]
    private fun getCurrentTimestamp(): String {
        val now = java.util.Calendar.getInstance().time
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
        val time = timeFormat.format(now)
        val date = dateFormat.format(now)
        return "[Time: $time | Date: $date]"
    }

    // Create a composable for the clock display to reuse in both modes
    @Composable
    fun ClockDisplay(modifier: Modifier = Modifier) {
        val infiniteTransition = rememberInfiniteTransition(label = "clock_transition")
        val currentTime = remember { mutableStateOf("") }
        val currentDate = remember { mutableStateOf("") }
        val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
        val dateFormatter = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }
        
        LaunchedEffect(Unit) {
            while(true) {
                val now = Date()
                currentTime.value = timeFormatter.format(now)
                currentDate.value = dateFormatter.format(now)
                delay(1000) // Update every second
            }
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
        ) {
            Text(
                text = currentTime.value,
                color = Color.White,
                fontSize = 192.sp,  // 300% larger than before
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = currentDate.value,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center
            )
        }
    }

    // Beautiful screensaver UI for Standard Mode idle state
    @Composable
    fun ScreensaverUI(onTap: () -> Unit, onViewLogs: () -> Unit) {
        var showSettings by remember { mutableStateOf(false) }
        val context = LocalContext.current
        var personalization by remember { mutableStateOf<SettingsManager.Personalization?>(null) }

        // Load current settings
        LaunchedEffect(Unit) {
            personalization = SettingsManager.getPersonalization(context)
        }

        val phrases = listOf(
            "Hi, I'm Luma.",
            "Tap to talk with me.",
            "How are you feeling today?",
            "Need to talk?",
            "Just tap if you'd like to talk."
        )
        
        // Smooth phrase transitions
        var currentPhraseIndex by remember { mutableStateOf(0) }
        var nextPhraseIndex by remember { mutableStateOf(1) }
        var isTransitioning by remember { mutableStateOf(false) }
        
        // Animation for text fade
        val transition = updateTransition(
            targetState = currentPhraseIndex,
            label = "phraseTransition"
        )
        
        val textAlpha by transition.animateFloat(
            label = "textAlpha",
            transitionSpec = {
                tween(2000, easing = LinearEasing)
            }
        ) { index ->
            if (isTransitioning) 0f else 1f
        }

        // Multiple animated gradients for fluid effect
        val infiniteTransition = rememberInfiniteTransition(label = "fluidAnimation")
        
        // Primary swirl
        val primaryAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(30000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "primaryRotation"
        )
        
        // Secondary swirl
        val secondaryAngle by infiniteTransition.animateFloat(
            initialValue = 180f,
            targetValue = 540f,
            animationSpec = infiniteRepeatable(
                animation = tween(25000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "secondaryRotation"
        )

        // Tertiary swirl
        val tertiaryAngle by infiniteTransition.animateFloat(
            initialValue = 90f,
            targetValue = 450f,
            animationSpec = infiniteRepeatable(
                animation = tween(35000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "tertiaryRotation"
        )
        
        // Breathing animation for scale
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(8000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathingScale"
        )

        // Color intensity animation
        val colorIntensity by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(6000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "colorIntensity"
        )

        // Handle phrase changes
        LaunchedEffect(Unit) {
            while (true) {
                delay(12000) // Wait 12 seconds
                isTransitioning = true
                delay(1000) // Fade out duration
                currentPhraseIndex = nextPhraseIndex
                nextPhraseIndex = (nextPhraseIndex + 1) % phrases.size
                isTransitioning = false
            }
        }

        // Calculate orbital positions
        val primaryAngleRad = (primaryAngle * (Math.PI / 180f)).toFloat()
        val secondaryAngleRad = (secondaryAngle * (Math.PI / 180f)).toFloat()
        val tertiaryAngleRad = (tertiaryAngle * (Math.PI / 180f)).toFloat()

        // Calculate positions using fractions (0.0 to 1.0)
        val orbitRadius = 0.2f // Radius of orbit as fraction of screen
        
        val primaryX = 0.5f + kotlin.math.cos(primaryAngleRad) * orbitRadius
        val primaryY = 0.5f + kotlin.math.sin(primaryAngleRad) * orbitRadius
        
        val secondaryX = 0.5f + kotlin.math.cos(secondaryAngleRad) * (orbitRadius * 0.8f)
        val secondaryY = 0.5f + kotlin.math.sin(secondaryAngleRad) * (orbitRadius * 0.8f)
        
        val tertiaryX = 0.5f + kotlin.math.cos(tertiaryAngleRad) * (orbitRadius * 1.2f)
        val tertiaryY = 0.5f + kotlin.math.sin(tertiaryAngleRad) * (orbitRadius * 1.2f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A)) // Dark base
                .background( // Primary gradient
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2C3E50).copy(alpha = colorIntensity * 0.7f),
                            Color(0xFF3498DB).copy(alpha = colorIntensity * 0.5f),
                            Color(0xFF1A1A1A).copy(alpha = 0f)
                        ),
                        center = androidx.compose.ui.geometry.Offset(primaryX, primaryY),
                        radius = 2000f * scale // Much larger radius
                    )
                )
                .background( // Secondary gradient
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8E44AD).copy(alpha = colorIntensity * 0.6f),
                            Color(0xFF2980B9).copy(alpha = colorIntensity * 0.4f),
                            Color(0xFF1A1A1A).copy(alpha = 0f)
                        ),
                        center = androidx.compose.ui.geometry.Offset(secondaryX, secondaryY),
                        radius = 1800f * scale // Much larger radius
                    )
                )
                .background( // Tertiary gradient
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFF39C12).copy(alpha = colorIntensity * 0.3f),
                            Color(0xFF1A1A1A).copy(alpha = 0f)
                        ),
                        center = androidx.compose.ui.geometry.Offset(tertiaryX, tertiaryY),
                        radius = 2200f * scale // Much larger radius
                    )
                )
                .clickable { onTap() }
        ) {
            // Settings button
            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
            ) {
                Text("⚙️", fontSize = 32.sp)
            }

            // Clock at the top
            ClockDisplay(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 150.dp)
            )

            // Centered text - moved below the clock
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 350.dp), // Push further down to leave room for clock
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = phrases[currentPhraseIndex],
                    color = Color(0xFFF5F5F5),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .alpha(textAlpha)
                )
            }

            // Settings dialog
            if (showSettings) {
                SettingsDialog(
                    initial = personalization,
                    onDismiss = { showSettings = false },
                    onSave = { name, pronouns, pronounsCustom, religion ->
                        mainScope.launch {
                            SettingsManager.setName(context, name)
                            SettingsManager.setPronouns(context, pronouns, pronounsCustom)
                            SettingsManager.setReligion(context, religion)
                            personalization = SettingsManager.getPersonalization(context)
                            showSettings = false
                            // Log personalization change
                            val pronounsDisplay = if (pronouns == "custom") pronounsCustom ?: "(custom)" else pronouns
                            val userInput = "Personalization updated: Name set to $name, pronouns set to $pronounsDisplay, religion set to $religion"
                            ConversationLogger.saveEntry(
                                context = context,
                                userInput = userInput,
                                aiResponse = "(Personalization updated)",
                                sentiment = "Neutral",
                                flags = ModerationResult(
                                    flagged = false,
                                    selfHarmScore = 0.0,
                                    violenceScore = 0.0,
                                    hateScore = 0.0,
                                    harassmentScore = 0.0,
                                    overallSentiment = "Neutral"
                                )
                            )
                        }
                    },
                    onViewLogs = onViewLogs
                )
            }
        }
    }

    // Placeholder for voice waveform animation (iridescent ripple)
    @Composable
    fun VoiceWaveformUI(onClose: () -> Unit, onViewLogs: () -> Unit) {
        var showSettings by remember { mutableStateOf(false) }
        val context = LocalContext.current
        var personalization by remember { mutableStateOf<SettingsManager.Personalization?>(null) }

        // Load current settings
        LaunchedEffect(Unit) {
            personalization = SettingsManager.getPersonalization(context)
        }

        // Get the most recent user and Luma messages
        val userMessage = conversation.lastOrNull { it.role == "user" }?.content ?: ""
        val lumaMessage = conversation.lastOrNull { it.role == "assistant" }?.content ?: ""

        // State for animation
        val userMsgVisible = userMessage.isNotBlank()
        val lumaMsgVisible = lumaMessage.isNotBlank()

        // Waveform state (calm if user speaking, peak if Luma speaking)
        val isLumaSpeaking = isSpeaking
        val isPatientSpeaking = !isLumaSpeaking && isListening

        val infiniteTransition = rememberInfiniteTransition(label = "waveform")
        val waveColors = listOf(
            listOf(Color(0xFF3498DB), Color(0xFF6DD5FA)), // blue
            listOf(Color(0xFF8E44AD), Color(0xFF6C3483)), // purple
            listOf(Color(0xFFF39C12), Color(0xFFF7CA18)), // orange
            listOf(Color(0xFF16A085), Color(0xFF1ABC9C)), // teal
            listOf(Color(0xFF2980B9), Color(0xFF2C3E50))  // deep blue
        )
        // Create multiple wave animations with different phases and speeds
        val waves: List<WaveData> = (0..4).map { index ->
            val phase = index * 0.2f
            val baseAmplitude = when (index) {
                0, 4 -> if (isLumaSpeaking) 0.5f else 0.2f  // Outer waves
                1, 3 -> if (isLumaSpeaking) 0.7f else 0.3f  // Middle waves
                2 -> if (isLumaSpeaking) 1.0f else 0.4f     // Center wave
                else -> 0.3f
            }
            val amplitude by infiniteTransition.animateFloat(
                initialValue = baseAmplitude,
                targetValue = baseAmplitude * (if (isLumaSpeaking) 1.2f else 1.0f),
                animationSpec = infiniteRepeatable(
                    animation = tween(2200 + (index * 200), easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "amplitude_$index"
            )
            val horizontalShift by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3500 + (index * 300), easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "shift_$index"
            )
            WaveData(amplitude, phase, horizontalShift)
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val sectionHeight = maxHeight / 2

            // Close button in top left
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(48.dp)
                    .zIndex(2f)
            ) {
                Text("✕", fontSize = 32.sp, color = Color.White)
            }

            // Draw the waves first, but move them down by 60.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sectionHeight)
                    .align(Alignment.TopCenter)
                    .padding(top = 210.dp) // 150 + 60 = 210 for extra space below clock
            ) {
                waves.forEachIndexed { index, wave ->
                    WaveShape(
                        wave = wave,
                        colors = waveColors[index % waveColors.size]
                    )
                }
            }

            // Draw the clock/date above the waves
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f) // Ensure clock is above waves
            ) {
                ClockDisplay(
                    modifier = Modifier
                        .padding(top = 24.dp)
                )
            }

            // Luma caption (bottom half)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sectionHeight)
                    .align(Alignment.BottomCenter)
                    .background(Color.Transparent)
                    .padding(top = 24.dp, bottom = 24.dp, start = 32.dp, end = 32.dp)
            ) {
                if (lumaMsgVisible) {
                    AnimatedMessageText(
                        message = lumaMessage,
                        isUser = false,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }

            // Settings button (remains top right)
            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
            ) {
                Text("⚙️", fontSize = 32.sp)
            }

            // Settings dialog (unchanged)
            if (showSettings) {
                SettingsDialog(
                    initial = personalization,
                    onDismiss = { showSettings = false },
                    onSave = { name, pronouns, pronounsCustom, religion ->
                        mainScope.launch {
                            SettingsManager.setName(context, name)
                            SettingsManager.setPronouns(context, pronouns, pronounsCustom)
                            SettingsManager.setReligion(context, religion)
                            personalization = SettingsManager.getPersonalization(context)
                            showSettings = false
                            // Log personalization change
                            val pronounsDisplay = if (pronouns == "custom") pronounsCustom ?: "(custom)" else pronouns
                            val userInput = "Personalization updated: Name set to $name, pronouns set to $pronounsDisplay, religion set to $religion"
                            ConversationLogger.saveEntry(
                                context = context,
                                userInput = userInput,
                                aiResponse = "(Personalization updated)",
                                sentiment = "Neutral",
                                flags = ModerationResult(
                                    flagged = false,
                                    selfHarmScore = 0.0,
                                    violenceScore = 0.0,
                                    hateScore = 0.0,
                                    harassmentScore = 0.0,
                                    overallSentiment = "Neutral"
                                )
                            )
                        }
                    },
                    onViewLogs = onViewLogs
                )
            }
        }
    }

    @Composable
    fun AnimatedMessageText(message: String, isUser: Boolean, modifier: Modifier = Modifier) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 60.sp,
            textAlign = if (isUser) TextAlign.End else TextAlign.Start,
            modifier = modifier
                .wrapContentHeight()
        )
    }

    @Composable
    fun WaveShape(wave: WaveData, colors: List<Color>) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val width = size.width
            val height = size.height
            val centerY = height / 2
            val path = Path()
            val points = mutableListOf<Offset>()
            for (x in 0..width.toInt() step 5) {
                val normalizedX = x / width
                val waveX = x.toFloat()
                val y = centerY + (kotlin.math.sin(normalizedX * 6 + wave.horizontalShift * 2 * Math.PI + wave.phase) *
                    kotlin.math.sin(normalizedX * 2.5 + wave.horizontalShift * Math.PI) *
                    wave.amplitude * height * 0.4f).toFloat()
                points.add(Offset(waveX, y))
            }
            if (points.size > 1) {
                path.moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size - 1) {
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    path.quadraticBezierTo(
                        p1.x, p1.y,
                        (p1.x + p2.x) / 2, (p1.y + p2.y) / 2
                    )
                }
            }
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(colors),
                style = Stroke(
                    width = 6.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }

    // Placeholder for voice waveform animation (for non-Compose calls)
    private fun showVoiceWaveform() {
        // For now, just log or trigger Compose state if needed
        Log.d("Luma", "showVoiceWaveform() called")
    }

    // Function to build the wake phrase
    private fun buildWakePhrase(): String {
        val name = personalization?.name?.takeIf { it.isNotBlank() }
        val now = java.util.Calendar.getInstance().time
        val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        val timeString = timeFormat.format(now)
        val builder = StringBuilder()
        if (name != null) {
            builder.append("Hi $name, ")
        }
        builder.append("It's $timeString. ")
        val phrase = wakePhrases.random()
        builder.append(phrase)
        return builder.toString()
    }

    private fun startContinuousRecognition(onResult: (String) -> Unit) {
        if (recognitionActive) return
        recognitionActive = true
        currentPartialResults.clear()
        Log.d("LumaRecognizer", "startContinuousRecognition called")
        
        val recognitionListener = object : android.speech.RecognitionListener {
            private var isListeningForSpeech = false
            private var lastPartialResult = ""
            private val resultBuffer = StringBuilder()
            private var bufferTimer: Job? = null

            override fun onResults(results: Bundle?) {
                val recognizedText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                Log.d("LumaRecognizer", "✅ Final recognized text: $recognizedText")

                if (!recognizedText.isNullOrBlank()) {
                    Log.d("LumaRecognizer", "🔄 Sending to model...")
                    mainScope.launch {
                        val pers = personalization
                        val timestamp = getCurrentTimestamp()
                        val userMessageForModel = buildUserMessageForModel(recognizedText, pers, timestamp)
                        val aiResponse = deepSeekClient.getResponse(userMessageForModel)
                        Log.d("LumaRecognizer", "🤖 Model replied: $aiResponse")
                        // Sentiment analysis and logging
                        val sentimentResult = analyzeSentimentWithOpenAI(recognizedText, BuildConfig.OPENAI_API_KEY)
                        if (sentimentResult != null) {
                            ConversationLogger.saveEntry(
                                context = this@MainActivity,
                                userInput = recognizedText,
                                aiResponse = aiResponse,
                                sentiment = sentimentResult.overallSentiment,
                                flags = sentimentResult
                            )
                        }
                        playTTSWithCallback(aiResponse) { startListening() }
                    }
                } else {
                    Log.w("LumaRecognizer", "⚠️ No valid recognition result")
                }
                restartRecognition()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                Log.d("LumaRecognizer", "onPartialResults called: $partialResults")
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val partialText = matches[0].trim()
                    Log.d("LumaRecognizer", "Partial recognized text: $partialText")
                    if (partialText.isNotBlank() && partialText != lastPartialResult) {
                        lastPartialResult = partialText
                        currentPartialResults.setLength(0)
                        currentPartialResults.append(partialText)
                    }
                }
            }

            override fun onBeginningOfSpeech() {
                isListeningForSpeech = true
                Log.d("LumaRecognizer", "onBeginningOfSpeech: Speech started")
            }

            override fun onEndOfSpeech() {
                isListeningForSpeech = false
                Log.d("LumaRecognizer", "onEndOfSpeech: Speech ended")
            }

            override fun onError(error: Int) {
                Log.e("LumaRecognizer", "onError called: $error (${getErrorText(error)})")
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> {
                        if (currentPartialResults.isNotBlank()) {
                            val partialText = currentPartialResults.toString().trim()
                            if (partialText.isNotBlank() && partialText != lastProcessedText) {
                                lastProcessedText = partialText
                                resultBuffer.append(partialText).append(" ")
                            }
                        }
                        restartRecognition()
                    }
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        val bufferedText = resultBuffer.toString().trim()
                        if (bufferedText.isNotBlank()) {
                            onResult(bufferedText)
                            resultBuffer.clear()
                        }
                        restartRecognition()
                    }
                    else -> {
                        mainScope.launch {
                            delay(500)
                            restartRecognition()
                        }
                    }
                }
            }

            private fun restartRecognition() {
                if (!recognitionActive) return
                restartRecognitionJob?.cancel()
                restartRecognitionJob = mainScope.launch {
                    delay(50)
                    if (recognitionActive && !isSpeaking) {
                        try {
                            Log.d("LumaRecognizer", "Restarting recognition...")
                            speechRecognizer.startListening(recognitionIntent)
                        } catch (e: Exception) {
                            Log.e("LumaRecognizer", "Error restarting recognition", e)
                        }
                    }
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("LumaRecognizer", "onReadyForSpeech: $params")
            }
            override fun onRmsChanged(rmsdB: Float) {
                Log.d("LumaRecognizer", "onRmsChanged: $rmsdB dB")
            }
            override fun onBufferReceived(buffer: ByteArray?) {
                Log.d("LumaRecognizer", "onBufferReceived: ${buffer?.size ?: 0} bytes")
            }
            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d("LumaRecognizer", "onEvent: type=$eventType, params=$params")
            }
        }

        speechRecognizer.setRecognitionListener(recognitionListener)
        try {
            Log.d("LumaRecognizer", "Calling startListening() with intent: $recognitionIntent")
            speechRecognizer.startListening(recognitionIntent)
        } catch (e: Exception) {
            Log.e("LumaRecognizer", "Error starting recognition", e)
        }
    }

    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error"
        }
    }

    private fun stopContinuousRecognition() {
        recognitionActive = false
        restartRecognitionJob?.cancel()
        try {
            speechRecognizer.stopListening()
        } catch (e: Exception) {
            Log.e("Luma", "Error stopping recognition", e)
        }
    }

    private fun initializeRecognitionIntent() {
        recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Shorter minimum length for more responsive feel
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
            // More forgiving silence thresholds
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            // Better quality settings
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)  // Use online for better quality
            putExtra("android.speech.extra.DICTATION_MODE", true)
        }
    }

    @Composable
    fun LumaCaption(text: String) {
        val scrollState = rememberScrollState()
        val lineHeightSp = 40.sp // Half of previous 80.sp
        val maxLines = 3
        val context = LocalContext.current

        // Smooth auto-scroll to bottom when text changes
        LaunchedEffect(text) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 32.sp, // Half of previous 64.sp
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = lineHeightSp,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                )
            }
        }
    }

    // Function to trim response to 5 sentences or 40 words, whichever comes first
    private fun trimResponseForSpeech(response: String): String {
        if (response.isBlank()) return response
        
        // Split into sentences (handles common sentence endings)
        val sentences = response.split(Regex("(?<=[.!?])\\s+"))
        
        // Count words
        val words = response.split(Regex("\\s+"))
        
        // If response is already short enough, return as is
        if (sentences.size <= 5 && words.size <= 40) {
            return response
        }
        
        // Take first 5 sentences or up to 40 words, whichever comes first
        val trimmedSentences = mutableListOf<String>()
        var wordCount = 0
        
        for (sentence in sentences) {
            val sentenceWords = sentence.split(Regex("\\s+"))
            if (wordCount + sentenceWords.size > 40) break
            if (trimmedSentences.size >= 5) break
            
            trimmedSentences.add(sentence)
            wordCount += sentenceWords.size
        }
        
        val trimmedResponse = trimmedSentences.joinToString(" ")
        return if (trimmedResponse != response) {
            "$trimmedResponse... (Let me know if you'd like to hear more.)"
        } else {
            trimmedResponse
        }
    }

    // Add this function to build the user message for the model
    private fun buildUserMessageForModel(
        recognizedText: String,
        personalization: SettingsManager.Personalization?,
        timestamp: String
    ): String {
        val name = personalization?.name?.takeIf { it.isNotBlank() } ?: "Unknown"
        val pronouns = personalization?.let {
            if (it.pronouns == "custom") it.pronounsCustom ?: "unspecified" else it.pronouns ?: "unspecified"
        } ?: "unspecified"
        val religion = personalization?.religion ?: "unspecified"
        return "$recognizedText. [User's name: $name] $timestamp [User's pronouns: $pronouns] [User's religion preference: $religion] Your response?"
    }

    override fun onDestroy() {
        super.onDestroy()
        stopContinuousRecognition()
        speechRecognizer.destroy()
        when (ttsClient) {
            is ElevenLabsTTSClient -> (ttsClient as ElevenLabsTTSClient).release()
            is OpenAITTSClient -> (ttsClient as OpenAITTSClient).release()
        }
        tts?.shutdown()
    }
}

@Composable
fun LumaCompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Orange,
            onPrimary = White,
            background = DarkGrey,
            surface = LightGrey,
            onSurface = White,
            secondary = Orange
        ),
        content = content
    )
}

@Composable
fun MainScreen(
    onTapToSpeak: () -> Unit,
    conversation: List<ChatMessage>,
    isLoading: Boolean,
    onSettingsClick: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGrey)
    ) {
        // Close button in top left
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(48.dp)
        ) {
            Text("✕", fontSize = 32.sp, color = Color.White)
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left side: Conversation
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .background(LightGrey, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                if (isLoading && conversation.isEmpty()) {
                    CircularProgressIndicator(color = Orange, modifier = Modifier.align(Alignment.Center))
                } else {
                    ConversationList(conversation)
                }
            }

            // Right side: Controls
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .padding(start = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Text("⚙️", fontSize = 32.sp)
                }

                Button(
                    onClick = onTapToSpeak,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) {
                    Text("Tap to Speak", fontWeight = FontWeight.Bold, fontSize = 36.sp)
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                CircularProgressIndicator(color = Orange, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun ConversationList(conversation: List<ChatMessage>) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .verticalScroll(scrollState)
            .fillMaxWidth()
    ) {
        for (msg in conversation) {
            val isUser = msg.role == "user"
            val bubbleColor = if (isUser) Orange else White.copy(alpha = 0.1f)
            val textColor = if (isUser) White else White
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .background(bubbleColor, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
    Text(
                    text = if (isUser) "You: ${msg.content}" else "Luma: ${msg.content}",
                    color = textColor,
                    fontSize = 24.sp,
                    fontWeight = if (isUser) FontWeight.Bold else FontWeight.Normal,
                    lineHeight = 32.sp
                )
            }
        }
    }
}

@Composable
fun SettingsDialog(
    initial: SettingsManager.Personalization?,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, String) -> Unit,
    onViewLogs: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var pronouns by remember { mutableStateOf(initial?.pronouns ?: "") }
    var pronounsCustom by remember { mutableStateOf(initial?.pronounsCustom ?: "") }
    var religion by remember { mutableStateOf(initial?.religion ?: "") }
    var showCustomPronouns by remember { mutableStateOf(pronouns == "custom") }

    // --- Voice selection state ---
    val context = LocalContext.current
    val tts = (context as? MainActivity)?.tts
    val allVoices = remember { tts?.voices?.toList()?.sortedBy { it.locale.toString() } ?: emptyList() }
    val englishUsVoices = allVoices.filter { it.locale.language == "en" && it.locale.country == "US" }
    var selectedVoice by remember { mutableStateOf(tts?.voice) }
    var playSampleJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val sampleText = "Hello! This is a sample of my voice."

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Patient Personalization", fontSize = 28.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onViewLogs,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) {
                    Text("View Logs", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Patient's Name") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("Pronouns", fontWeight = FontWeight.Bold)
                DropdownMenuBox(
                    options = listOf("he/him", "she/her", "they/them", "custom"),
                    selected = pronouns,
                    onSelected = {
                        pronouns = it
                        showCustomPronouns = it == "custom"
                        if (it != "custom") pronounsCustom = ""
                    }
                )
                if (showCustomPronouns) {
                    OutlinedTextField(
                        value = pronounsCustom,
                        onValueChange = { pronounsCustom = it },
                        label = { Text("Custom Pronouns") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Religious Preference", fontWeight = FontWeight.Bold)
                DropdownMenuBox(
                    options = listOf("Christian", "Jewish", "Muslim", "Buddhist", "Spiritual", "None", "Other"),
                    selected = religion,
                    onSelected = { religion = it }
                )
                Spacer(Modifier.height(24.dp))
                // --- English (US) Voice Selector ---
                Text("English (US) TTS Voice", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                if (englishUsVoices.isEmpty()) {
                    Text("No English (US) voices found on this device.", color = Color.Red)
                } else {
                    Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        englishUsVoices.forEach { voice ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                RadioButton(
                                    selected = selectedVoice?.name == voice.name,
                                    onClick = {
                                        selectedVoice = voice
                                        tts?.voice = voice
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(voice.name, fontWeight = FontWeight.SemiBold)
                                    Text("Locale: ${voice.locale}", fontSize = 12.sp)
                                }
                                Button(onClick = {
                                    playSampleJob?.cancel()
                                    playSampleJob = coroutineScope.launch {
                                        tts?.voice = voice
                                        tts?.speak(sampleText, TextToSpeech.QUEUE_FLUSH, null, "sample")
                                    }
                                }) {
                                    Text("Play Sample")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, pronouns, pronounsCustom, religion) }) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DropdownMenuBox(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.ifBlank { "Select..." })
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun LogViewerDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val logs = remember {
        val logFile = File(context.filesDir, "conversation_log.jsonl")
        if (logFile.exists()) {
            logFile.readLines().mapNotNull {
                try { JSONObject(it) } catch (_: Exception) { null }
            }
        } else emptyList()
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Conversation Logs", fontSize = 28.sp, fontWeight = FontWeight.Bold) },
        text = {
            Box(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                Column {
                    if (logs.isEmpty()) {
                        Text("No logs found.", fontSize = 20.sp)
                    } else {
                        logs.forEach { log ->
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text("🕒 ${log.optString("timestamp")}", fontSize = 16.sp, color = Color.Gray)
                                Text("👤 User: ${log.optString("userInput")}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("🤖 Luma: ${log.optString("aiResponse")}", fontSize = 20.sp)
                                Text("Sentiment: ${log.optString("sentiment")}", fontSize = 16.sp)
                                if (log.optBoolean("flagged", false)) {
                                    Text("⚠️ Flagged", color = Color.Red, fontSize = 16.sp)
                                }
                                Text("Scores: self-harm=${log.optDouble("selfHarmScore")}, violence=${log.optDouble("violenceScore")}, hate=${log.optDouble("hateScore")}, harassment=${log.optDouble("harassmentScore")}", fontSize = 14.sp, color = Color.Gray)
                                Divider(Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onClose) { Text("Close") }
        }
    )
}