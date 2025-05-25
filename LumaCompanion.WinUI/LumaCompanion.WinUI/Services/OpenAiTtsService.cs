using System;
using System.IO;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Threading.Tasks;
using Windows.Media.Core;
using Windows.Media.Playback;
using Windows.Storage;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;

namespace LumaCompanion.WinUI.Services
{
    public class OpenAiTtsService : ITtsService
    {
        private readonly HttpClient _httpClient;
        private readonly IConfiguration _configuration;
        private readonly ILogger<OpenAiTtsService> _logger;
        private MediaPlayer _mediaPlayer;
        private string _tempFilePath;
        private Action _onPlaybackCompleteCallback;

        private const string OpenAiTtsApiUrl = "https://api.openai.com/v1/audio/speech";

        public OpenAiTtsService(HttpClient httpClient, IConfiguration configuration, ILogger<OpenAiTtsService> logger)
        {
            _httpClient = httpClient;
            _configuration = configuration;
            _logger = logger;
            // Initialize MediaPlayer here if it's to be reused, or create new instances in PlayAudioFileAsync
        }

        public async Task SpeakAsync(string text, Action onPlaybackComplete)
        {
            _onPlaybackCompleteCallback = onPlaybackComplete;
            var apiKey = _configuration["ApiKeys:OpenAI"];

            if (string.IsNullOrEmpty(apiKey))
            {
                _logger?.LogError("OpenAI API key not found in configuration for TTS.");
                _onPlaybackCompleteCallback?.Invoke();
                return;
            }

            _httpClient.DefaultRequestHeaders.Clear();
            _httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", apiKey);

            var requestBody = new
            {
                model = "tts-1",
                voice = "shimmer", // Or any other preferred voice
                input = text,
                response_format = "mp3"
            };

            try
            {
                var response = await _httpClient.PostAsJsonAsync(OpenAiTtsApiUrl, requestBody);

                if (response.IsSuccessStatusCode)
                {
                    var audioData = await response.Content.ReadAsByteArrayAsync();
                    _tempFilePath = Path.Combine(Path.GetTempPath(), $"{Guid.NewGuid()}.mp3");
                    await File.WriteAllBytesAsync(_tempFilePath, audioData);

                    await PlayAudioFileAsync(_tempFilePath);
                }
                else
                {
                    var errorContent = await response.Content.ReadAsStringAsync();
                    _logger?.LogError($"Error from OpenAI TTS API: {response.StatusCode} - {errorContent}");
                    _onPlaybackCompleteCallback?.Invoke(); // Invoke callback even on API error
                }
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Exception while calling OpenAI TTS API or playing audio.");
                CleanupAndCallback(); // Ensure cleanup and callback on exception
            }
        }

        private async Task PlayAudioFileAsync(string filePath)
        {
            try
            {
                StorageFile storageFile = await StorageFile.GetFileFromPathAsync(filePath);
                
                // Ensure previous player is disposed if any
                if (_mediaPlayer != null)
                {
                    _mediaPlayer.MediaEnded -= MediaPlayer_MediaEnded;
                    _mediaPlayer.Dispose();
                }

                _mediaPlayer = new MediaPlayer();
                _mediaPlayer.Source = MediaSource.CreateFromStorageFile(storageFile);
                _mediaPlayer.MediaEnded += MediaPlayer_MediaEnded;
                _mediaPlayer.Play();
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, $"Error playing audio file: {filePath}");
                CleanupAndCallback(); // Ensure cleanup and callback on playback error
            }
        }

        private void MediaPlayer_MediaEnded(MediaPlayer sender, object args)
        {
            sender.MediaEnded -= MediaPlayer_MediaEnded; // Unsubscribe
            CleanupAndCallback();
        }

        private void CleanupAndCallback()
        {
            // Clean up the temporary file
            if (!string.IsNullOrEmpty(_tempFilePath) && File.Exists(_tempFilePath))
            {
                try
                {
                    File.Delete(_tempFilePath);
                    _logger?.LogInformation($"Temporary TTS file deleted: {_tempFilePath}");
                    _tempFilePath = null; 
                }
                catch (Exception ex)
                {
                    _logger?.LogError(ex, $"Error deleting temporary TTS file: {_tempFilePath}");
                }
            }
            
            // Invoke the callback
            _onPlaybackCompleteCallback?.Invoke();
            _onPlaybackCompleteCallback = null; // Reset callback after invocation

            // Optional: Dispose MediaPlayer if it's not meant to be reused across different SpeakAsync calls
            // If SpeakAsync can be called rapidly, you might want to manage the MediaPlayer instance differently
            // For now, we create a new one per call in PlayAudioFileAsync, so this specific disposal here might be redundant
            // if PlayAudioFileAsync always cleans up the previous one.
            // However, if PlayAudioFileAsync fails before assigning to _mediaPlayer, this could be a safety net.
             if (_mediaPlayer != null)
             {
                // _mediaPlayer.Dispose(); // This might be problematic if MediaEnded is triggered by a disposed player.
                // _mediaPlayer = null;
             }
        }
    }
}
