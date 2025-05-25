using System;
using System.Threading.Tasks;
using Windows.Media.SpeechRecognition;
using Windows.Globalization;
using Microsoft.Extensions.Logging; // Assuming ILogger is available
using Windows.ApplicationModel.Core;
using Windows.UI.Core; // For Dispatcher if needed for UI updates from events (though service itself shouldn't directly update UI)

namespace LumaCompanion.WinUI.Services
{
    public class WindowsSpeechRecognitionService : ISpeechRecognitionService, IDisposable
    {
        private SpeechRecognizer _speechRecognizer;
        private SpeechRecognitionState _currentState;
        private readonly ILogger<WindowsSpeechRecognitionService> _logger;
        private bool _isInitialized = false;
        private CoreDispatcher _dispatcher; // To marshal event invocations to the main thread if consumers need it

        public event Action<string> SpeechRecognized;
        public event Action<string> PartialSpeechRecognized;
        public event Action<SpeechRecognitionState> RecognitionStateChanged;

        public SpeechRecognitionState CurrentState
        {
            get => _currentState;
            private set
            {
                if (_currentState != value)
                {
                    _currentState = value;
                    // Use dispatcher if events are consumed by UI elements
                    _dispatcher?.TryRunAsync(CoreDispatcherPriority.Normal, () => RecognitionStateChanged?.Invoke(_currentState));
                }
            }
        }

        public WindowsSpeechRecognitionService(ILogger<WindowsSpeechRecognitionService> logger = null)
        {
            _logger = logger;
            _currentState = SpeechRecognitionState.Idle;
            // Get the dispatcher for the main UI thread. This is crucial if event handlers update UI.
            // This might need to be passed in if the service is created on a background thread.
            // For now, assuming it's created on or can access the main UI thread's dispatcher.
            _dispatcher = CoreApplication.MainView?.CoreWindow?.Dispatcher;
            if (_dispatcher == null) {
                 _logger?.LogWarning("Could not obtain CoreDispatcher. Event marshalling to UI thread might not work if service is on a background thread.");
            }
        }

        public async Task<bool> InitializeAsync()
        {
            if (_isInitialized) return true;

            CurrentState = SpeechRecognitionState.Initializing;
            _logger?.LogInformation("Initializing speech recognizer...");

            // 1. Check Microphone Permissions (Simplified Check)
            // A full permission request flow is complex and often involves UI interaction.
            // Here, we rely on the capability being declared in Package.appxmanifest.
            // Actual permission is granted by the user when the app first attempts to use the microphone.
            // We can check if a recognizer can be created.
            bool permissionGranted = false;
            try
            {
                // Attempt to create a SpeechRecognizer. If microphone access is denied by policy or user, this might fail.
                // However, typically it doesn't throw here but later during StartAsync if access is truly denied.
                // The best check is to see if a language is supported.
                if (SpeechRecognizer.SupportedGrammarLanguages.Count == 0) {
                    _logger?.LogError("No supported grammar languages found. Speech recognition may not be available.");
                     CurrentState = SpeechRecognitionState.Error;
                    return false;
                }
                if (SpeechRecognizer.SystemSpeechLanguage != null) {
                    permissionGranted = true; // If system language is available, assume basic capability.
                } else {
                     _logger?.LogError("System speech language is null. Speech recognition may not be available.");
                     CurrentState = SpeechRecognitionState.Error;
                    return false;
                }

            }
            catch (Exception ex) // Catches HRESULT 0x80045509 if speech privacy policy blocks mic access
            {
                _logger?.LogError(ex, "Error checking microphone permissions or speech recognizer availability.");
                CurrentState = SpeechRecognitionState.Error;
                return false;
            }

            if (!permissionGranted)
            {
                _logger?.LogWarning("Microphone permission not granted or speech recognizer not available.");
                CurrentState = SpeechRecognitionState.Error;
                return false;
            }


            try
            {
                _speechRecognizer = new SpeechRecognizer(SpeechRecognizer.SystemSpeechLanguage);

                // Configure for free-form dictation
                var dictationConstraint = new SpeechRecognitionTopicConstraint(SpeechRecognitionScenario.Dictation, "dictation");
                _speechRecognizer.Constraints.Add(dictationConstraint);

                await _speechRecognizer.CompileConstraintsAsync();
                _logger?.LogInformation("Speech recognizer constraints compiled.");

                // Subscribe to events
                _speechRecognizer.StateChanged += SpeechRecognizer_StateChanged;
                _speechRecognizer.ContinuousRecognitionSession.ResultGenerated += ContinuousRecognitionSession_ResultGenerated;
                _speechRecognizer.HypothesisGenerated += SpeechRecognizer_HypothesisGenerated;
                
                _isInitialized = true;
                CurrentState = SpeechRecognitionState.Idle;
                _logger?.LogInformation("Speech recognizer initialized successfully.");
                return true;
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Exception during speech recognizer initialization.");
                CurrentState = SpeechRecognitionState.Error;
                if (_speechRecognizer != null)
                {
                    DisposeRecognizerResources();
                }
                return false;
            }
        }

        public async Task StartRecognitionAsync()
        {
            if (!_isInitialized)
            {
                _logger?.LogWarning("Speech recognizer not initialized. Call InitializeAsync first.");
                return;
            }

            if (CurrentState == SpeechRecognitionState.Listening)
            {
                _logger?.LogInformation("Speech recognition already active.");
                return;
            }
            
            if (_speechRecognizer.State == SpeechRecognizerState.Idle || _speechRecognizer.State == SpeechRecognizerState.Paused)
            {
                try
                {
                    _logger?.LogInformation("Starting continuous speech recognition session...");
                    CurrentState = SpeechRecognitionState.Listening; // Optimistically set state
                    await _speechRecognizer.ContinuousRecognitionSession.StartAsync();
                }
                catch (Exception ex)
                {
                    _logger?.LogError(ex, "Error starting speech recognition session.");
                    CurrentState = SpeechRecognitionState.Error;
                }
            }
        }

        public async Task StopRecognitionAsync()
        {
            if (!_isInitialized || _speechRecognizer == null) return;

            if (CurrentState == SpeechRecognitionState.Listening)
            {
                try
                {
                    _logger?.LogInformation("Stopping continuous speech recognition session...");
                    await _speechRecognizer.ContinuousRecognitionSession.StopAsync();
                    // State will be updated by the StateChanged event
                }
                catch (Exception ex)
                {
                    _logger?.LogError(ex, "Error stopping speech recognition session.");
                    CurrentState = SpeechRecognitionState.Error;
                }
            }
        }

        private void SpeechRecognizer_StateChanged(SpeechRecognizer sender, SpeechRecognizerStateChangedEventArgs args)
        {
            _logger?.LogInformation($"Speech recognizer state changed to: {args.State}");
            switch (args.State)
            {
                case SpeechRecognizerState.Idle:
                    CurrentState = SpeechRecognitionState.Idle;
                    break;
                case SpeechRecognizerState.Capturing:
                case SpeechRecognizerState.Processing:
                    CurrentState = SpeechRecognitionState.Listening; // Consider these as part of listening
                    break;
                case SpeechRecognizerState.SoundStarted:
                case SpeechRecognizerState.SoundEnded:
                    // These are intermediate states, could map to Listening or a more granular custom state
                    break; 
                case SpeechRecognizerState.SpeechDetected:
                     CurrentState = SpeechRecognitionState.Listening;
                    break;
                case SpeechRecognizerState.Paused:
                    CurrentState = SpeechRecognitionState.Paused;
                    break;
                case SpeechRecognizerState.SoundTimeout:
                case SpeechRecognizerState.SpeechNotRecognized:
                    CurrentState = SpeechRecognitionState.Error; // Or a more specific state like "NoSpeechDetected"
                    _logger?.LogWarning($"Speech recognizer reported: {args.State}");
                    // Optionally, restart recognition here if desired for continuous "always-on" listening
                    // Task.Run(async () => await StartRecognitionAsync());
                    break;
                default:
                    CurrentState = SpeechRecognitionState.Error;
                    break;
            }
        }

        private void ContinuousRecognitionSession_ResultGenerated(SpeechContinuousRecognitionSession sender, SpeechContinuousRecognitionResultGeneratedEventArgs args)
        {
            if (!string.IsNullOrWhiteSpace(args.Result?.Text))
            {
                _logger?.LogInformation($"Final speech result: {args.Result.Text}");
                _dispatcher?.TryRunAsync(CoreDispatcherPriority.Normal, () => SpeechRecognized?.Invoke(args.Result.Text));
            }
        }

        private void SpeechRecognizer_HypothesisGenerated(SpeechRecognizer sender, SpeechRecognitionHypothesisGeneratedEventArgs args)
        {
            if (!string.IsNullOrWhiteSpace(args.Hypothesis?.Text))
            {
                 // This can be very verbose, log selectively or at a lower level if needed
                // _logger?.LogTrace($"Partial speech result: {args.Hypothesis.Text}"); 
                _dispatcher?.TryRunAsync(CoreDispatcherPriority.Normal, () => PartialSpeechRecognized?.Invoke(args.Hypothesis.Text));
            }
        }
        
        private void DisposeRecognizerResources()
        {
            if (_speechRecognizer != null)
            {
                _speechRecognizer.ContinuousRecognitionSession.ResultGenerated -= ContinuousRecognitionSession_ResultGenerated;
                _speechRecognizer.HypothesisGenerated -= SpeechRecognizer_HypothesisGenerated;
                _speechRecognizer.StateChanged -= SpeechRecognizer_StateChanged;
                
                if (_speechRecognizer.State != SpeechRecognizerState.Idle) {
                    try {
                        // Attempt to stop if not idle, but this might not always succeed cleanly if already disposing
                        Task.Run(async () => await _speechRecognizer.ContinuousRecognitionSession.StopAsync()).Wait(TimeSpan.FromSeconds(1));
                        Task.Run(async () => await _speechRecognizer.ContinuousRecognitionSession.CancelAsync()).Wait(TimeSpan.FromSeconds(1));
                    } catch(Exception ex) {
                        _logger?.LogWarning(ex, "Exception during stopping/cancelling recognizer in Dispose.");
                    }
                }
                _speechRecognizer.Dispose();
                _speechRecognizer = null;
                _logger?.LogInformation("Speech recognizer disposed.");
            }
        }

        public void Dispose()
        {
            DisposeRecognizerResources();
            GC.SuppressFinalize(this);
        }

        ~WindowsSpeechRecognitionService()
        {
            DisposeRecognizerResources();
        }
    }
}
