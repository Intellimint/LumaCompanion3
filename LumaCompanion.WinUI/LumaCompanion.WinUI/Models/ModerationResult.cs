using System;

namespace LumaCompanion.WinUI.Models
{
    public class ModerationResult
    {
        public bool Flagged { get; set; }
        public double? SelfHarmScore { get; set; }
        public double? ViolenceScore { get; set; }
        public double? HateScore { get; set; }
        public double? HarassmentScore { get; set; }
        public string? OverallSentiment { get; set; } // e.g., "Positive", "Negative", "Neutral" - can be derived or placeholder
    }
}
