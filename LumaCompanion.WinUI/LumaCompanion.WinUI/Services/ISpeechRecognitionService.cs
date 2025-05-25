using System;
using System.Threading.Tasks;

namespace LumaCompanion.WinUI.Services
{
    public interface ISpeechRecognitionService
    {
        /// <summary>
        /// Initializes the speech recognizer, checking for permissions.
        /// </summary>
        /// <returns>True if initialization is successful and permissions granted, false otherwise.</returns>
        Task<bool> InitializeAsync();

        /// <summary>
        /// Starts the continuous speech recognition session.
        /// </summary>
        Task StartRecognitionAsync();

        /// <summary>
        /// Stops the continuous speech recognition session.
        /// </summary>
        Task StopRecognitionAsync();

        /// <summary>
        /// Event triggered when a final speech recognition result is generated.
        /// </summary>
        event Action<string> SpeechRecognized;

        /// <summary>
        /// Event triggered when a partial speech recognition result (hypothesis) is generated.
        /// </summary>
        event Action<string> PartialSpeechRecognized;

        /// <summary>
        /// Event triggered when the speech recognition engine's state changes.
        /// </summary>
        event Action<SpeechRecognitionState> RecognitionStateChanged;

        /// <summary>
        /// Gets the current state of the speech recognition service.
        /// </summary>
        SpeechRecognitionState CurrentState { get; }
    }
}
