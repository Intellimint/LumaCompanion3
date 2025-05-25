using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading.Tasks;
using LumaCompanion.WinUI.Models;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging; // Added for potential logging

namespace LumaCompanion.WinUI.Services
{
    // Data Models
    public class DeepSeekMessage
    {
        [JsonPropertyName("role")]
        public string Role { get; set; }

        [JsonPropertyName("content")]
        public string Content { get; set; }

        public DeepSeekMessage(string role, string content)
        {
            Role = role;
            Content = content;
        }
    }

    public class DeepSeekChatRequest
    {
        [JsonPropertyName("model")]
        public string Model { get; set; } = "deepseek/chat"; // Default model

        [JsonPropertyName("messages")]
        public List<DeepSeekMessage> Messages { get; set; }

        [JsonPropertyName("temperature")]
        public double Temperature { get; set; } = 0.7; // Default temperature

        public DeepSeekChatRequest(List<DeepSeekMessage> messages, double temperature = 0.7)
        {
            Messages = messages;
            Temperature = temperature;
        }
    }

    public class DeepSeekChoice
    {
        [JsonPropertyName("message")]
        public DeepSeekMessage Message { get; set; }
    }

    public class DeepSeekChatResponse
    {
        [JsonPropertyName("choices")]
        public List<DeepSeekChoice> Choices { get; set; }
    }

    public class OpenRouterService
    {
        private readonly HttpClient _httpClient;
        private readonly IConfiguration _configuration;
        private readonly ILogger<OpenRouterService> _logger; // Optional: for logging
        private const string OpenRouterApiUrl = "https://openrouter.ai/api/v1/chat/completions";

        public OpenRouterService(HttpClient httpClient, IConfiguration configuration, ILogger<OpenRouterService> logger)
        {
            _httpClient = httpClient;
            _configuration = configuration;
            _logger = logger; // Optional
        }

        private string ConstructSystemPrompt(PersonalizationSettings personalization)
        {
            var sb = new StringBuilder("You are Luma, a friendly and helpful AI assistant. ");

            if (personalization != null)
            {
                if (!string.IsNullOrWhiteSpace(personalization.Name))
                {
                    sb.Append($"The user's name is {personalization.Name}. ");
                }
                if (!string.IsNullOrWhiteSpace(personalization.Pronouns))
                {
                    if (personalization.Pronouns.Equals("custom", StringComparison.OrdinalIgnoreCase) && !string.IsNullOrWhiteSpace(personalization.PronounsCustom))
                    {
                        sb.Append($"The user's pronouns are {personalization.PronounsCustom}. ");
                    }
                    else if (!personalization.Pronouns.Equals("custom", StringComparison.OrdinalIgnoreCase))
                    {
                        sb.Append($"The user's pronouns are {personalization.Pronouns}. ");
                    }
                }
                if (!string.IsNullOrWhiteSpace(personalization.Religion))
                {
                    sb.Append($"The user's religion is {personalization.Religion}. ");
                }
            }
            // Add any other general instructions for Luma here.
            sb.Append("Please be respectful, concise, and informative in your responses.");
            return sb.ToString();
        }

        public async Task<string> GetResponseWithHistoryAsync(List<DeepSeekMessage> conversationHistory, PersonalizationSettings personalization)
        {
            var apiKey = _configuration["ApiKeys:DeepSeek"];
            if (string.IsNullOrEmpty(apiKey))
            {
                _logger?.LogError("DeepSeek API key not found in configuration.");
                return "Error: DeepSeek API key not configured.";
            }

            _httpClient.DefaultRequestHeaders.Clear();
            _httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", apiKey);
            _httpClient.DefaultRequestHeaders.Add("HTTP-Referer", "https://luma.companion"); // Replace with your actual site URL or app name
            _httpClient.DefaultRequestHeaders.Add("X-Title", "Luma Companion"); // Replace with your actual site URL or app name


            var systemPrompt = ConstructSystemPrompt(personalization);
            var messagesWithSystemPrompt = new List<DeepSeekMessage> { new DeepSeekMessage("system", systemPrompt) };
            messagesWithSystemPrompt.AddRange(conversationHistory);

            var request = new DeepSeekChatRequest(messagesWithSystemPrompt);

            try
            {
                var response = await _httpClient.PostAsJsonAsync(OpenRouterApiUrl, request);

                if (response.IsSuccessStatusCode)
                {
                    var chatResponse = await response.Content.ReadFromJsonAsync<DeepSeekChatResponse>();
                    return chatResponse?.Choices?.FirstOrDefault()?.Message?.Content?.Trim() ?? "No response content.";
                }
                else
                {
                    var errorContent = await response.Content.ReadAsStringAsync();
                    _logger?.LogError($"Error from OpenRouter API: {response.StatusCode} - {errorContent}");
                    return $"Error: Failed to get response from AI. Status: {response.StatusCode}";
                }
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Exception while calling OpenRouter API.");
                return "Error: An exception occurred while communicating with the AI.";
            }
        }

        public async IAsyncEnumerable<string> StreamResponseWithHistoryAsync(List<DeepSeekMessage> conversationHistory, PersonalizationSettings personalization)
        {
            // Placeholder implementation:
            var fullResponse = await GetResponseWithHistoryAsync(conversationHistory, personalization);
            yield return fullResponse;
        }
    }
}
