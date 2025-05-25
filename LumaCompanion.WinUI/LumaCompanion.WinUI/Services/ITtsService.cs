using System;
using System.Threading.Tasks;

namespace LumaCompanion.WinUI.Services
{
    public interface ITtsService
    {
        /// <summary>
        /// Speaks the given text using a TTS engine and invokes a callback upon completion.
        /// </summary>
        /// <param name="text">The text to be spoken.</param>
        /// <param name="onPlaybackComplete">Action to be invoked when TTS audio playback finishes.</param>
        /// <returns>A task representing the asynchronous operation.</returns>
        Task SpeakAsync(string text, Action onPlaybackComplete);
    }
}
