namespace LumaCompanion.WinUI.Services
{
    public enum SpeechRecognitionState
    {
        Idle,
        Initializing,
        Listening,
        Paused, // Corresponds to SpeechRecognizerState.Paused
        Error   // Custom state for general errors or SpeechRecognizerState.SoundTimeout, SpeechRecognizerState.SpeechNotRecognized
    }
}
