namespace LumaCompanion.WinUI.ViewModels
{
    public enum LumaState
    {
        Idle,         // Waiting for user interaction or speech input
        Listening,    // Actively listening for speech
        Thinking,     // Processing user input (e.g., calling AI service)
        Speaking,     // Playing TTS output
        Error         // An error has occurred
    }
}
