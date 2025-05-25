using System;

namespace LumaCompanion.WinUI.Models
{
    public class LogEntry
    {
        public DateTime Timestamp { get; set; }
        public string UserInput { get; set; }
        public string AiResponse { get; set; }
        public string Sentiment { get; set; } // e.g., "Positive", "Negative", "Neutral", "Flagged"
        public bool Flagged { get; set; } // From moderation

        // Detailed scores from moderation
        public double? SelfHarmScore { get; set; }
        public double? ViolenceScore { get; set; }
        public double? HateScore { get; set; }
        public double? HarassmentScore { get; set; }

        public LogEntry()
        {
            Timestamp = DateTime.UtcNow; // Default to UTC now
            UserInput = string.Empty;
            AiResponse = string.Empty;
            Sentiment = "N/A";
        }
    }
}
