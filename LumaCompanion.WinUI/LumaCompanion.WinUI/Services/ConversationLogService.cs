using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading.Tasks;
using LumaCompanion.WinUI.Models;
using Microsoft.Extensions.Logging; // Assuming ILogger is available if needed
using Windows.Storage;

namespace LumaCompanion.WinUI.Services
{
    public class ConversationLogService : IConversationLogService
    {
        private const string LogFileName = "conversation_log.jsonl";
        private readonly ILogger<ConversationLogService> _logger;

        public ConversationLogService(ILogger<ConversationLogService> logger = null)
        {
            _logger = logger;
        }

        public async Task SaveEntryAsync(LogEntry entry)
        {
            try
            {
                StorageFolder localFolder = ApplicationData.Current.LocalFolder;
                StorageFile logFile = await localFolder.CreateFileAsync(LogFileName, CreationCollisionOption.OpenIfExists);

                string jsonEntry = JsonSerializer.Serialize(entry);
                
                // Append the new log entry as a new line
                // Using FileIO.AppendLinesAsync for simplicity and robustness
                await FileIO.AppendLinesAsync(logFile, new[] { jsonEntry });

                _logger?.LogInformation("Log entry saved successfully.");
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Error saving log entry.");
                // Optionally, rethrow or handle more gracefully depending on app requirements
                // throw; 
            }
        }

        public async Task<List<LogEntry>> LoadEntriesAsync()
        {
            var entries = new List<LogEntry>();
            try
            {
                StorageFolder localFolder = ApplicationData.Current.LocalFolder;
                StorageFile logFile = await localFolder.GetFileAsync(LogFileName);

                if (logFile == null)
                {
                    _logger?.LogInformation("Log file not found. Returning empty list.");
                    return entries; // File does not exist
                }

                IList<string> lines = await FileIO.ReadLinesAsync(logFile);
                foreach (var line in lines)
                {
                    if (string.IsNullOrWhiteSpace(line)) continue;

                    try
                    {
                        LogEntry entry = JsonSerializer.Deserialize<LogEntry>(line);
                        if (entry != null)
                        {
                            entries.Add(entry);
                        }
                    }
                    catch (JsonException jsonEx)
                    {
                        _logger?.LogWarning(jsonEx, $"Skipping corrupted log line: {line}");
                        // Skip corrupted line
                    }
                }
                _logger?.LogInformation($"Loaded {entries.Count} log entries successfully.");
                return entries.OrderByDescending(e => e.Timestamp).ToList(); // Show newest first
            }
            catch (FileNotFoundException)
            {
                _logger?.LogInformation("Log file not found. Returning empty list.");
                return entries; // File does not exist
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Error loading log entries.");
                return entries; // Return whatever was loaded, or empty on major error
            }
        }
    }
}
