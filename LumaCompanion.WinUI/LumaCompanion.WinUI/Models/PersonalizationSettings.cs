using System;

namespace LumaCompanion.WinUI.Models
{
    public class PersonalizationSettings
    {
        public string? Name { get; set; }
        public string? Pronouns { get; set; } // e.g., "he/him", "she/her", "they/them", "custom"
        public string? PronounsCustom { get; set; } // Used if Pronouns is "custom"
        public string? Religion { get; set; }
    }
}
