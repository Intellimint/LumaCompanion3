using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using LumaCompanion.WinUI.Models;
using Microsoft.Extensions.Logging;
using Windows.Storage;

namespace LumaCompanion.WinUI.Services
{
    public class SettingsService : ISettingsService
    {
        private const string SettingsFileName = "luma_settings.dat";
        private readonly ILogger<SettingsService> _logger;
        private static readonly byte[] s_entropy = Encoding.Unicode.GetBytes("LumaCompanionEntropy"); // Optional but recommended

        public SettingsService(ILogger<SettingsService> logger = null)
        {
            _logger = logger;
        }

        public async Task SavePersonalizationAsync(PersonalizationSettings settings)
        {
            try
            {
                string jsonSettings = JsonSerializer.Serialize(settings);
                byte[] userData = Encoding.UTF8.GetBytes(jsonSettings);

                // Encrypt the data using DPAPI
                byte[] encryptedData = ProtectedData.Protect(userData, s_entropy, DataProtectionScope.CurrentUser);

                StorageFolder localFolder = ApplicationData.Current.LocalFolder;
                StorageFile settingsFile = await localFolder.CreateFileAsync(SettingsFileName, CreationCollisionOption.ReplaceExisting);

                using (var stream = await settingsFile.OpenStreamForWriteAsync())
                {
                    await stream.WriteAsync(encryptedData, 0, encryptedData.Length);
                }
                _logger?.LogInformation("Personalization settings saved successfully.");
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Error saving personalization settings.");
                // Optionally, rethrow or handle more gracefully depending on app requirements
                throw; 
            }
        }

        public async Task<PersonalizationSettings> LoadPersonalizationAsync()
        {
            try
            {
                StorageFolder localFolder = ApplicationData.Current.LocalFolder;
                StorageFile settingsFile = await localFolder.GetFileAsync(SettingsFileName);

                if (settingsFile == null)
                {
                    _logger?.LogInformation("Settings file not found. Returning default personalization settings.");
                    return new PersonalizationSettings(); // Or some other default
                }

                byte[] encryptedData;
                using (var stream = await settingsFile.OpenStreamForReadAsync())
                {
                    using (var memoryStream = new MemoryStream())
                    {
                        await stream.CopyToAsync(memoryStream);
                        encryptedData = memoryStream.ToArray();
                    }
                }

                if (encryptedData == null || encryptedData.Length == 0)
                {
                    _logger?.LogWarning("Settings file is empty. Returning default personalization settings.");
                    return new PersonalizationSettings();
                }

                // Decrypt the data using DPAPI
                byte[] decryptedData = ProtectedData.Unprotect(encryptedData, s_entropy, DataProtectionScope.CurrentUser);

                string jsonSettings = Encoding.UTF8.GetString(decryptedData);
                PersonalizationSettings settings = JsonSerializer.Deserialize<PersonalizationSettings>(jsonSettings);
                
                _logger?.LogInformation("Personalization settings loaded successfully.");
                return settings ?? new PersonalizationSettings();
            }
            catch (FileNotFoundException)
            {
                _logger?.LogInformation("Settings file not found. Returning default personalization settings.");
                return new PersonalizationSettings(); // File does not exist, return default
            }
            catch (CryptographicException ex)
            {
                _logger?.LogError(ex, "Error decrypting personalization settings. Potentially corrupted or unreadable. Returning default settings.");
                // Consider deleting the corrupted file or marking it as corrupted
                // await DeleteCorruptedSettingsFile(); 
                return new PersonalizationSettings(); // Return default on decryption error
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Error loading personalization settings. Returning default settings.");
                return new PersonalizationSettings(); // Return default on other errors
            }
        }

        // Optional: Method to delete corrupted settings file
        private async Task DeleteCorruptedSettingsFile()
        {
            try
            {
                StorageFolder localFolder = ApplicationData.Current.LocalFolder;
                StorageFile settingsFile = await localFolder.GetFileAsync(SettingsFileName);
                if (settingsFile != null)
                {
                    await settingsFile.DeleteAsync();
                    _logger?.LogInformation("Corrupted settings file deleted.");
                }
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Error deleting corrupted settings file.");
            }
        }
    }
}
