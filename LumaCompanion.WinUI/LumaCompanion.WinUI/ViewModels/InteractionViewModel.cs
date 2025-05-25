using LumaCompanion.WinUI.Models;
using LumaCompanion.WinUI.Services;
using Microsoft.Extensions.Logging; 
using System;
using System.Collections.Generic; 
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Linq; 
using System.Runtime.CompilerServices; 
using System.Threading.Tasks;

namespace LumaCompanion.WinUI.ViewModels
{
    public class InteractionViewModel : INotifyPropertyChanged
    {
        // Services - Now injected
        private readonly ISpeechRecognitionService _speechRecognitionService;
        private readonly ITtsService _ttsService;
        private readonly IOpenRouterService _openRouterService;
        private readonly IOpenAiService _openAiService;
        private readonly ISettingsService _settingsService;
        private readonly IConversationLogService _logService;
        private readonly ILogger<InteractionViewModel> _logger;

        // Properties
        private ObservableCollection<ChatMessageViewModel> _conversationMessages;
        public ObservableCollection<ChatMessageViewModel> ConversationMessages
        {
            get => _conversationMessages;
            set => SetProperty(ref _conversationMessages, value);
        }

        private string _lumaCaptionText;
        public string LumaCaptionText
        {
            get => _lumaCaptionText;
            set => SetProperty(ref _lumaCaptionText, value);
        }

        private LumaState _currentLumaState;
        public LumaState CurrentLumaState
        {
            get => _currentLumaState;
            set
            {
                if (SetProperty(ref _currentLumaState, value))
                {
                    OnPropertyChanged(nameof(StatusText)); 
                    _logger?.LogInformation($"LumaState changed to: {value}");
                }
            }
        }

        private bool _isMicrophoneEnabled;
        public bool IsMicrophoneEnabled
        {
            get => _isMicrophoneEnabled;
            set => SetProperty(ref _isMicrophoneEnabled, value);
        }

        public string StatusText => $"Luma State: {CurrentLumaState} | Mic: {(IsMicrophoneEnabled ? "On" : "Off/Unavailable")}";
        
        private PersonalizationSettings _personalizationSettings;
        private const int MaxHistoryMessages = 10; 


        // Constructor with Service Injection
        public InteractionViewModel(
            ISpeechRecognitionService speechRecognitionService,
            ITtsService ttsService,
            IOpenRouterService openRouterService,
            IOpenAiService openAiService,
            ISettingsService settingsService,
            IConversationLogService logService,
            ILogger<InteractionViewModel> logger = null) 
        {
            _speechRecognitionService = speechRecognitionService ?? throw new ArgumentNullException(nameof(speechRecognitionService));
            _ttsService = ttsService ?? throw new ArgumentNullException(nameof(ttsService));
            _openRouterService = openRouterService ?? throw new ArgumentNullException(nameof(openRouterService));
            _openAiService = openAiService ?? throw new ArgumentNullException(nameof(openAiService));
            _settingsService = settingsService ?? throw new ArgumentNullException(nameof(settingsService));
            _logService = logService ?? throw new ArgumentNullException(nameof(logService));
            _logger = logger;

            ConversationMessages = new ObservableCollection<ChatMessageViewModel>();
            CurrentLumaState = LumaState.Idle;
            LumaCaptionText = "Tap Luma to speak."; 
            IsMicrophoneEnabled = false; 
        }

        public async Task LoadAsync()
        {
            try
            {
                _personalizationSettings = await _settingsService.LoadPersonalizationAsync();
                if (_personalizationSettings == null)
                {
                    _logger?.LogWarning("Personalization settings loaded as null, creating new default instance.");
                    _personalizationSettings = new PersonalizationSettings();
                }
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Failed to load personalization settings. Using default.");
                _personalizationSettings = new PersonalizationSettings();
            }

            bool recognizerInitialized = false;
            try
            {
                recognizerInitialized = await _speechRecognitionService.InitializeAsync();
            }
            catch (Exception ex)
            {
                 _logger?.LogError(ex, "Speech recognizer initialization failed.");
                 CurrentLumaState = LumaState.Error;
                 LumaCaptionText = "Error initializing speech recognition.";
            }

            if (recognizerInitialized)
            {
                IsMicrophoneEnabled = true;
                CurrentLumaState = LumaState.Idle;
                LumaCaptionText = "Tap Luma to speak or type your message.";
                _speechRecognitionService.SpeechRecognized += OnSpeechRecognized;
                _speechRecognitionService.PartialSpeechRecognized += OnPartialSpeechRecognized;
                _speechRecognitionService.RecognitionStateChanged += OnRecognitionStateChanged;
            }
            else
            {
                IsMicrophoneEnabled = false;
                CurrentLumaState = LumaState.Error;
                LumaCaptionText = "Microphone not available or permission denied.";
            }
            OnPropertyChanged(nameof(StatusText)); 
        }

        public async Task UnloadAsync()
        {
            if (_speechRecognitionService != null)
            {
                await _speechRecognitionService.StopRecognitionAsync();
                _speechRecognitionService.SpeechRecognized -= OnSpeechRecognized;
                _speechRecognitionService.PartialSpeechRecognized -= OnPartialSpeechRecognized;
                _speechRecognitionService.RecognitionStateChanged -= OnRecognitionStateChanged;
                // Let the DI container handle disposal if it manages lifetime, or App.xaml.cs if manual
                // For this task, assuming services passed in might be used elsewhere, so don't dispose here.
                // If InteractionViewModel exclusively owns them, then (_speechRecognitionService as IDisposable)?.Dispose();
            }
            // Same for _ttsService
        }

        public async Task StartListeningAsync()
        {
            if (!IsMicrophoneEnabled)
            {
                LumaCaptionText = "Cannot start listening: Microphone is not enabled.";
                CurrentLumaState = LumaState.Error;
                _logger?.LogWarning("StartListeningAsync called but microphone is not enabled.");
                return;
            }

            if (CurrentLumaState == LumaState.Idle || CurrentLumaState == LumaState.Speaking || CurrentLumaState == LumaState.Error)
            {
                LumaCaptionText = "Listening..."; 
                await _speechRecognitionService.StartRecognitionAsync();
            }
            else
            {
                _logger?.LogInformation($"StartListeningAsync called when LumaState is {CurrentLumaState}, no action taken.");
            }
        }

        public async Task StopListeningAsync() 
        {
            if (IsMicrophoneEnabled && (CurrentLumaState == LumaState.Listening || CurrentLumaState == LumaState.Thinking))
            {
                _logger?.LogInformation("StopListeningAsync called, stopping speech recognition.");
                await _speechRecognitionService.StopRecognitionAsync();
            }
        }

        private async void OnSpeechRecognized(string recognizedText)
        {
            if (string.IsNullOrWhiteSpace(recognizedText))
            {
                _logger?.LogInformation("Speech recognized but text is empty or whitespace.");
                if (CurrentLumaState == LumaState.Listening) CurrentLumaState = LumaState.Idle;
                return;
            }
            
            CurrentLumaState = LumaState.Thinking;
            LumaCaptionText = $"You: {recognizedText}";
            _logger?.LogInformation($"User said: {recognizedText}");
            ConversationMessages.Add(new ChatMessageViewModel("User", recognizedText));

            string aiResponseText = "Sorry, I encountered an issue."; 
            ModerationResult moderationResult = null;
            bool sentimentError = false;
            bool aiError = false;

            try
            {
                _logger?.LogInformation("Analyzing sentiment...");
                moderationResult = await _openAiService.AnalyzeSentimentAsync(recognizedText);
                _logger?.LogInformation($"Sentiment analysis complete. Flagged: {moderationResult?.Flagged}");
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Error during sentiment analysis.");
                sentimentError = true;
            }

            try
            {
                LumaCaptionText = "Luma is thinking...";
                _logger?.LogInformation("Getting AI response...");
                var aiHistory = PrepareAiHistory();
                aiResponseText = await _openRouterService.GetResponseWithHistoryAsync(aiHistory, _personalizationSettings);
                _logger?.LogInformation($"AI responded: {aiResponseText}");
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Error getting response from OpenRouterService.");
                aiResponseText = "I had trouble understanding that. Could you try again?";
                aiError = true;
                CurrentLumaState = LumaState.Error; 
            }
            
            ConversationMessages.Add(new ChatMessageViewModel("Luma", aiResponseText));

            if (!aiError)
            {
                CurrentLumaState = LumaState.Speaking;
                LumaCaptionText = $"{aiResponseText}"; 
                try
                {
                    _logger?.LogInformation("Starting TTS...");
                    await _ttsService.SpeakAsync(aiResponseText, () =>
                    {
                        _logger?.LogInformation("TTS playback complete.");
                        CurrentLumaState = LumaState.Idle;
                        LumaCaptionText = "Tap Luma to speak.";
                    });
                }
                catch (Exception ex)
                {
                    _logger?.LogError(ex, "Error during TTS playback.");
                    CurrentLumaState = LumaState.Error; 
                    LumaCaptionText = "Sorry, I had trouble speaking.";
                }
            } else {
                LumaCaptionText = aiResponseText; 
            }

            try
            {
                _logger?.LogInformation("Saving log entry...");
                var logEntry = new LogEntry
                {
                    Timestamp = DateTime.UtcNow,
                    UserInput = recognizedText,
                    AiResponse = aiResponseText,
                    Sentiment = moderationResult?.OverallSentiment ?? (sentimentError ? "Error" : "N/A"),
                    Flagged = moderationResult?.Flagged ?? false,
                    SelfHarmScore = moderationResult?.SelfHarmScore,
                    ViolenceScore = moderationResult?.ViolenceScore,
                    HateScore = moderationResult?.HateScore,
                    HarassmentScore = moderationResult?.HarassmentScore
                };
                await _logService.SaveEntryAsync(logEntry);
                _logger?.LogInformation("Log entry saved.");
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Error saving conversation log.");
            }
            
            if (aiError && CurrentLumaState != LumaState.Speaking) { 
                CurrentLumaState = LumaState.Error;
            }
        }

        private List<DeepSeekMessage> PrepareAiHistory()
        {
            return ConversationMessages
                .TakeLast(MaxHistoryMessages)
                .Select(vm => new DeepSeekMessage(vm.Role == "User" ? "user" : "assistant", vm.Content)) // Ensure role is "user" or "assistant"
                .ToList();
        }

        private void OnPartialSpeechRecognized(string partialText)
        {
            if (CurrentLumaState == LumaState.Listening) 
            {
                LumaCaptionText = $"You (listening...): {partialText}";
            }
        }

        private void OnRecognitionStateChanged(SpeechRecognitionState newState)
        {
            _logger?.LogInformation($"SpeechRecognitionService state changed to: {newState}");
            switch (newState)
            {
                case SpeechRecognitionState.Idle:
                    if (CurrentLumaState != LumaState.Thinking && CurrentLumaState != LumaState.Speaking)
                    {
                        CurrentLumaState = LumaState.Idle;
                        LumaCaptionText = "Tap Luma to speak.";
                    }
                    break;
                case SpeechRecognitionState.Listening:
                    CurrentLumaState = LumaState.Listening;
                    LumaCaptionText = "Listening...";
                    break;
                case SpeechRecognitionState.Paused: 
                    LumaCaptionText = "Speech input paused.";
                    break;
                case SpeechRecognitionState.Error:
                    if (CurrentLumaState == LumaState.Listening || CurrentLumaState == LumaState.Idle)
                    {
                        CurrentLumaState = LumaState.Error;
                        LumaCaptionText = "Error with speech recognition.";
                    }
                    break;
            }
        }

        public event PropertyChangedEventHandler PropertyChanged;
        protected bool SetProperty<T>(ref T storage, T value, [CallerMemberName] string propertyName = null)
        {
            if (Equals(storage, value)) return false;
            storage = value;
            OnPropertyChanged(propertyName);
            return true;
        }
        protected void OnPropertyChanged([CallerMemberName] string propertyName = null)
        {
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
        }
    }
}
