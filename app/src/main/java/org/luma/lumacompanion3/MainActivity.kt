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
    private lateinit var ttsClient: OpenAITTSClient
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening()
        } else {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            ttsClient = OpenAITTSClient(this)

        setContent {
                LumaCompanionTheme {
                    var showSettings by remember { mutableStateOf(false) }
                    var demoMode by remember { mutableStateOf(true) }
                    var isIdle by remember { mutableStateOf(false) }
                    var composeIsListeningOrSpeaking by remember { mutableStateOf(false) }
                    val context = LocalContext.current

                    // Keep isListeningOrSpeaking in sync
                    LaunchedEffect(composeIsListeningOrSpeaking) {
                        isListeningOrSpeaking = composeIsListeningOrSpeaking
                    }

                    // Load personalization and demo mode on launch
                    LaunchedEffect(Unit) {
                        personalization = SettingsManager.getPersonalization(context)
                        demoMode = SettingsManager.getDemoMode(context)
                        if (!demoMode) {
                            isIdle = true
                        }
                    }

                    // Keep isIdleState in sync with Compose state
                    LaunchedEffect(isIdle) { isIdleState = isIdle }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = DarkGrey
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            if (!demoMode && isIdle) {
                                ScreensaverUI(
                                    onTap = {
                                        isIdle = false
                                        composeIsListeningOrSpeaking = true
                                        persistentListening = true
                                        isListening = false
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
                                                playTTSWithCallback(
                                                    "I'll stay quiet for now. Just tap me or say 'Hello Luma' if you'd like to talk."
                                                ) {}
                                            }
                                        }
                                    }
                                )
                            } else if (!demoMode && composeIsListeningOrSpeaking) {
                                VoiceWaveformUI()
                            } else {
                                MainScreen(
                                    onTapToSpeak = {
                                        if (demoMode) {
                                            checkPermissionAndStartListening(
                                                onResult = { recognizedText ->
                                                    mainScope.launch {
                                                        isLoading = true
                                                        val timestamp = getCurrentTimestamp()
                                                        val userMessageForApi = "User said: $recognizedText $timestamp"
                                                        val updatedConversation = conversation + ChatMessage("user", recognizedText)
                                                        val updatedConversationForApi = conversation + ChatMessage("user", userMessageForApi)
                                                        val trimmedConversation = trimConversationToTokenLimit(updatedConversationForApi, 5000)
                                                        val pers = SettingsManager.getPersonalization(context)
                                                        personalization = pers
                                                        val response = deepSeekClient.getResponseWithHistory(trimmedConversation, pers)
                                                        val newConversation = updatedConversation + ChatMessage("assistant", response)
                                                        conversation = newConversation
                                                        isLoading = false
                                                        playTTSWithCallback(response) {}
                                                    }
                                                }
                                            )
                                        } else {
                                            isIdle = false
                                            composeIsListeningOrSpeaking = true
                                            persistentListening = true
                                            isListening = false
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
                                                    playTTSWithCallback(
                                                        "I'll stay quiet for now. Just tap me or say 'Hello Luma' if you'd like to talk."
                                                    ) {}
                                                }
                                            }
                                        }
                                    },
                                    conversation = conversation,
                                    isLoading = isLoading,
                                    onSettingsClick = { showSettings = true }
                                )
                            }
                            if (showSettings) {
                                SettingsDialog(
                                    initial = personalization,
                                    demoMode = demoMode,
                                    onDismiss = { showSettings = false },
                                    onSave = { name, pronouns, pronounsCustom, religion, demoModeValue ->
                                        mainScope.launch {
                                            SettingsManager.setName(context, name)
                                            SettingsManager.setPronouns(context, pronouns, pronounsCustom)
                                            SettingsManager.setReligion(context, religion)
                                            SettingsManager.setDemoMode(context, demoModeValue)
                                            personalization = SettingsManager.getPersonalization(context)
                                            demoMode = demoModeValue
                                            showSettings = false
                                        }
                                    }
                                )
                            }
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

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }

        try {
            speechRecognizer.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val recognizedText = matches[0]
                        if (recognizedText.isNotBlank()) {
                            onResult(recognizedText)
                            // Wait for TTS to finish before restarting listening
                            shouldRestartListening = true
                        } else {
                            restartListeningIfNeeded()
                        }
                    } else {
                        restartListeningIfNeeded()
                    }
                    isListening = false
                }

                override fun onError(error: Int) {
                    isListening = false
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> {
                            // Only restart if we're not speaking and should be listening
                            if (!isSpeaking && persistentListening) {
                                restartListeningIfNeeded()
                            }
                        }
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            if (!isSpeaking && persistentListening) {
                                restartListeningIfNeeded()
                            }
                        }
                        else -> {
                            Log.d("Luma", "Speech recognition error: $error")
                            if (!isSpeaking && persistentListening) {
                                restartListeningIfNeeded()
                            }
                        }
                    }
                }

                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("Luma", "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d("Luma", "Speech started")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Update waveform animation based on RMS
                    // We can use this to make the waveform responsive to actual speech
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d("Luma", "Speech ended")
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // We could use this to show partial results while speaking
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            Log.e("Luma", "Error starting speech recognition", e)
            restartListeningIfNeeded()
        }
    }

    private fun restartListeningIfNeeded() {
        if (!persistentListening || isSpeaking) return
        
        mainScope.launch {
            delay(1000) // Short delay before restarting
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
        isSpeaking = true
        shouldRestartListening = false
        mainScope.launch {
            try {
                ttsClient.playShimmerVoice(text)
                // Assuming average speaking rate of 150 words per minute
                val wordsCount = text.split(" ").size
                val estimatedDuration = (wordsCount / 2.5f) * 1000 // milliseconds
                delay(estimatedDuration.toLong())
            } finally {
                isSpeaking = false
                shouldRestartListening = true
                onComplete()
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
            showVoiceWaveform()
            mainScope.launch {
                setIsLoading(true)
                resetInactivityTimer {
                    mainScope.launch {
                        persistentListening = false
                        setIsIdle(true)
                        playTTSWithCallback(
                            "I'll stay quiet for now. Just tap me or say 'Hello Luma' if you'd like to talk."
                        ) {}
                    }
                }
                
                val timestamp = getCurrentTimestamp()
                val userMessageForApi = "User said: $recognizedText $timestamp"
                val updatedConversation = conversationState() + ChatMessage("user", recognizedText)
                val updatedConversationForApi = conversationState() + ChatMessage("user", userMessageForApi)
                val trimmedConversation = trimConversationToTokenLimit(updatedConversationForApi, 5000)
                val pers = SettingsManager.getPersonalization(context)
                val response = deepSeekClient.getResponseWithHistory(trimmedConversation, pers)
                val newConversation = updatedConversation + ChatMessage("assistant", response)
                setConversation(newConversation)
                setIsLoading(false)
                
                // Play TTS and restart listening when done
                playTTSWithCallback(response) {
                    if (persistentListening && !isIdleState) {
                        restartListeningIfNeeded()
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

    // Beautiful screensaver UI for Standard Mode idle state
    @Composable
    fun ScreensaverUI(onTap: () -> Unit) {
        var showSettings by remember { mutableStateOf(false) }
        val context = LocalContext.current
        var personalization by remember { mutableStateOf<SettingsManager.Personalization?>(null) }
        var demoMode by remember { mutableStateOf(true) }

        // Load current settings
        LaunchedEffect(Unit) {
            personalization = SettingsManager.getPersonalization(context)
            demoMode = SettingsManager.getDemoMode(context)
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

            // Centered text
            Box(
                modifier = Modifier.fillMaxSize(),
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
                    demoMode = demoMode,
                    onDismiss = { showSettings = false },
                    onSave = { name, pronouns, pronounsCustom, religion, demoModeValue ->
                        mainScope.launch {
                            SettingsManager.setName(context, name)
                            SettingsManager.setPronouns(context, pronouns, pronounsCustom)
                            SettingsManager.setReligion(context, religion)
                            SettingsManager.setDemoMode(context, demoModeValue)
                            personalization = SettingsManager.getPersonalization(context)
                            demoMode = demoModeValue
                            showSettings = false
                        }
                    }
                )
            }
        }
    }

    // Placeholder for voice waveform animation (iridescent ripple)
    @Composable
    fun VoiceWaveformUI() {
        var showSettings by remember { mutableStateOf(false) }
        val context = LocalContext.current
        var personalization by remember { mutableStateOf<SettingsManager.Personalization?>(null) }
        var demoMode by remember { mutableStateOf(true) }

        // Load current settings
        LaunchedEffect(Unit) {
            personalization = SettingsManager.getPersonalization(context)
            demoMode = SettingsManager.getDemoMode(context)
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
            val sectionHeight = maxHeight / 3
            // User caption (top third, scrollable)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sectionHeight)
                    .align(Alignment.TopCenter)
                    .background(Color.Transparent)
                    .padding(top = 24.dp, bottom = 24.dp, end = 32.dp)
            ) {
                if (userMsgVisible) {
                    AnimatedMessageText(
                        message = userMessage,
                        isUser = true,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
            // Waveform (middle third)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sectionHeight)
                    .align(Alignment.Center)
            ) {
                waves.forEachIndexed { index, wave ->
                    WaveShape(
                        wave = wave,
                        colors = waveColors[index % waveColors.size]
                    )
                }
            }
            // Luma caption (bottom third, scrollable)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sectionHeight)
                    .align(Alignment.BottomCenter)
                    .background(Color.Transparent)
                    .padding(top = 24.dp, bottom = 24.dp, start = 32.dp)
            ) {
                if (lumaMsgVisible) {
                    AnimatedMessageText(
                        message = lumaMessage,
                        isUser = false,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
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
                    demoMode = demoMode,
                    onDismiss = { showSettings = false },
                    onSave = { name, pronouns, pronounsCustom, religion, demoModeValue ->
                        mainScope.launch {
                            SettingsManager.setName(context, name)
                            SettingsManager.setPronouns(context, pronouns, pronounsCustom)
                            SettingsManager.setReligion(context, religion)
                            SettingsManager.setDemoMode(context, demoModeValue)
                            personalization = SettingsManager.getPersonalization(context)
                            demoMode = demoModeValue
                            showSettings = false
                        }
                    }
                )
            }
        }
    }

    @Composable
    fun AnimatedMessageText(message: String, isUser: Boolean, modifier: Modifier = Modifier) {
        val words = message.split(" ")
        val visibleWords = remember { mutableStateListOf<String>() }
        val alpha = remember { Animatable(0f) }
        LaunchedEffect(message) {
            visibleWords.clear()
            alpha.snapTo(0f)
            for ((i, word) in words.withIndex()) {
                visibleWords.add(word)
                alpha.animateTo(1f, animationSpec = tween(300, easing = LinearEasing))
                delay(120)
            }
        }
        Text(
            text = visibleWords.joinToString(" "),
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 60.sp,
            textAlign = if (isUser) TextAlign.End else TextAlign.Start,
            modifier = modifier
                .alpha(alpha.value)
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

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        ttsClient.release()
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
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGrey)
    ) {
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
    demoMode: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var pronouns by remember { mutableStateOf(initial?.pronouns ?: "") }
    var pronounsCustom by remember { mutableStateOf(initial?.pronounsCustom ?: "") }
    var religion by remember { mutableStateOf(initial?.religion ?: "") }
    var showCustomPronouns by remember { mutableStateOf(pronouns == "custom") }
    var demoModeValue by remember { mutableStateOf(demoMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Patient Personalization", fontSize = 28.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Demo Mode", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = demoModeValue, onCheckedChange = { demoModeValue = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, pronouns, pronounsCustom, religion, demoModeValue) }) {
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