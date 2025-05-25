using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading.Tasks;
using LumaCompanion.WinUI.Models;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging; // Added for potential logging

namespace LumaCompanion.WinUI.Services
{
    // Data Models for OpenAI Moderation
    public class OpenAiModerationRequest
    {
        [JsonPropertyName("input")]
        public string Input { get; set; }

        public OpenAiModerationRequest(string input)
        {
            Input = input;
        }
    }

    public class OpenAiModerationCategoryScores
    {
        [JsonPropertyName("sexual")]
        public double Sexual { get; set; }

        [JsonPropertyName("hate")]
        public double Hate { get; set; }

        [JsonPropertyName("harassment")]
        public double Harassment { get; set; }

        [JsonPropertyName("self-harm")]
        public double SelfHarm { get; set; }

        [JsonPropertyName("sexual/minors")]
        public double SexualMinors { get; set; }

        [JsonPropertyName("hate/threatening")]
        public double HateThreatening { get; set; }

        [JsonPropertyName("violence/graphic")]
        public double ViolenceGraphic { get; set; }

        [JsonPropertyName("self-harm/intent")]
        public double SelfHarmIntent { get; set; }

        [JsonPropertyName("self-harm/instructions")]
        public double SelfHarmInstructions { get; set; }

        [JsonPropertyName("harassment/threatening")]
        public double HarassmentThreatening { get; set; }

        [JsonPropertyName("violence")]
        public double Violence { get; set; }
    }

    public class OpenAiModerationResultItem
    {
        [JsonPropertyName("flagged")]
        public bool Flagged { get; set; }

        [JsonPropertyName("categories")]
        public Dictionary<string, bool> Categories { get; set; }

        [JsonPropertyName("category_scores")]
        public OpenAiModerationCategoryScores CategoryScores { get; set; }
    }

    public class OpenAiModerationResponse
    {
        [JsonPropertyName("id")]
        public string Id { get; set; }

        [JsonPropertyName("model")]
        public string Model { get; set; }

        [JsonPropertyName("results")]
        public List<OpenAiModerationResultItem> Results { get; set; }
    }


    public class OpenAiService
    {
        private readonly HttpClient _httpClient;
        private readonly IConfiguration _configuration;
        private readonly ILogger<OpenAiService> _logger; // Optional: for logging
        private const string OpenAiModerationApiUrl = "https://api.openai.com/v1/moderations";

        public OpenAiService(HttpClient httpClient, IConfiguration configuration, ILogger<OpenAiService> logger)
        {
            _httpClient = httpClient;
            _configuration = configuration;
            _logger = logger; // Optional
        }

        public async Task<ModerationResult> AnalyzeSentimentAsync(string text)
        {
            var apiKey = _configuration["ApiKeys:OpenAI"];
            if (string.IsNullOrEmpty(apiKey))
            {
                _logger?.LogError("OpenAI API key not found in configuration.");
                // Return a default/error ModerationResult
                return new ModerationResult { Flagged = false, OverallSentiment = "Error: API Key Not Configured" };
            }

            _httpClient.DefaultRequestHeaders.Clear();
            _httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", apiKey);

            var request = new OpenAiModerationRequest(text);

            try
            {
                var response = await _httpClient.PostAsJsonAsync(OpenAiModerationApiUrl, request);

                if (response.IsSuccessStatusCode)
                {
                    var moderationResponse = await response.Content.ReadFromJsonAsync<OpenAiModerationResponse>();
                    if (moderationResponse != null && moderationResponse.Results.Any())
                    {
                        var resultItem = moderationResponse.Results.First();
                        return new ModerationResult
                        {
                            Flagged = resultItem.Flagged,
                            SelfHarmScore = resultItem.CategoryScores.SelfHarm,
                            ViolenceScore = resultItem.CategoryScores.Violence,
                            HateScore = resultItem.CategoryScores.Hate,
                            HarassmentScore = resultItem.CategoryScores.Harassment,
                            OverallSentiment = resultItem.Flagged ? "Flagged" : "Not Flagged" // Simplified sentiment
                        };
                    }
                    return new ModerationResult { Flagged = false, OverallSentiment = "No results from API" };
                }
                else
                {
                    var errorContent = await response.Content.ReadAsStringAsync();
                    _logger?.LogError($"Error from OpenAI Moderation API: {response.StatusCode} - {errorContent}");
                    return new ModerationResult { Flagged = false, OverallSentiment = $"Error: API Status {response.StatusCode}" };
                }
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Exception while calling OpenAI Moderation API.");
                return new ModerationResult { Flagged = false, OverallSentiment = "Error: Exception during API call" };
            }
        }
    }
}
