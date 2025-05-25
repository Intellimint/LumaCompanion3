using System;
using System.Threading.Tasks;
using Windows.System.Display;
using Windows.Graphics.Display; // For BrightnessOverride
using Microsoft.Extensions.Logging; // Assuming available for logging

namespace LumaCompanion.WinUI.Helpers
{
    public class DisplayManager
    {
        private DisplayRequest _displayRequest;
        private BrightnessOverride _brightnessOverride; // May not be usable in all contexts
        private readonly ILogger<DisplayManager> _logger;

        public DisplayManager(ILogger<DisplayManager> logger = null)
        {
            _logger = logger;
        }

        // --- Keep Screen On ---
        public void ActivateDisplay()
        {
            try
            {
                if (_displayRequest == null)
                {
                    _displayRequest = new DisplayRequest();
                }
                _displayRequest.RequestActive();
                _logger?.LogInformation("Display request activated - screen will stay on.");
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Error activating display request.");
            }
        }

        public void ReleaseDisplay()
        {
            try
            {
                if (_displayRequest != null)
                {
                    _displayRequest.RequestRelease();
                    _displayRequest = null; // Release the object
                    _logger?.LogInformation("Display request released - screen can turn off normally.");
                }
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Error releasing display request.");
            }
        }

        // --- Adjust Brightness by Time of Day ---
        public async Task AdjustBrightnessForTimeOfDayAsync()
        {
            int currentHour = DateTime.Now.Hour;
            double targetBrightness = 0.7; // Default brightness

            if (currentHour >= 6 && currentHour < 9) // Morning (6 AM - 8:59 AM)
            {
                targetBrightness = 0.65;
            }
            else if (currentHour >= 9 && currentHour < 18) // Daytime (9 AM - 5:59 PM)
            {
                targetBrightness = 0.9;
            }
            else if (currentHour >= 18 && currentHour < 22) // Evening (6 PM - 9:59 PM)
            {
                targetBrightness = 0.65;
            }
            else // Night (10 PM - 5:59 AM)
            {
                targetBrightness = 0.4;
            }

            _logger?.LogInformation($"Calculated target brightness: {targetBrightness} for hour {currentHour}.");

            try
            {
                // BrightnessOverride might only work for UWP apps or require specific capabilities
                // not typically available or straightforward in unpackaged WinUI 3 desktop apps.
                // It also might not affect all displays (e.g., external monitors on a desktop).

                if (_brightnessOverride == null)
                {
                    // GetForCurrentView() might fail if not called from a UWP UI context.
                    // For WinUI 3 desktop, this is a known point of difficulty.
                    try
                    {
                        _brightnessOverride = BrightnessOverride.GetForCurrentView();
                    }
                    catch (Exception ex)
                    {
                        _logger?.LogWarning(ex, "Failed to get BrightnessOverride for current view. This API might not be available in the current context (e.g., unpackaged desktop app). Brightness adjustment will be skipped.");
                        // Log that direct brightness control is not available/supported.
                        System.Diagnostics.Debug.WriteLine("[DisplayManager] BrightnessOverride.GetForCurrentView() failed. Brightness adjustment skipped.");
                        return; // Exit if we can't get the override object
                    }
                }

                if (_brightnessOverride.IsSupported)
                {
                    // Check if override is already active with the same level to avoid redundant calls
                    if (_brightnessOverride.IsOverrideActive && Math.Abs(_brightnessOverride.BrightnessLevel - targetBrightness) < 0.01)
                    {
                        _logger?.LogInformation($"Brightness override already active at target level {targetBrightness}. No change needed.");
                        return;
                    }

                    _brightnessOverride.SetBrightnessLevel(targetBrightness, DisplayBrightnessOverrideOptions.None);
                    _brightnessOverride.StartOverride();
                    _logger?.LogInformation($"Brightness override started. Level set to {targetBrightness}.");
                }
                else
                {
                    _logger?.LogWarning("Brightness override is not supported on this device/display. Brightness adjustment skipped.");
                    // Log that brightness override is not supported.
                    System.Diagnostics.Debug.WriteLine("[DisplayManager] Brightness override not supported. Brightness adjustment skipped.");
                }
            }
            catch (Exception ex)
            {
                // Log the exception and the fact that brightness adjustment failed.
                _logger?.LogError(ex, "Exception occurred during brightness adjustment.");
                System.Diagnostics.Debug.WriteLine($"[DisplayManager] Exception during brightness adjustment: {ex.Message}. Brightness adjustment skipped.");
                // If GetForCurrentView() succeeded but StartOverride() fails, we might want to nullify _brightnessOverride
                // so it's re-fetched next time, though if IsSupported is false, it won't retry.
            }
        }

        // Call this when the app is suspending or exiting to stop overriding brightness
        public void StopBrightnessOverride()
        {
            try
            {
                if (_brightnessOverride != null && _brightnessOverride.IsOverrideActive)
                {
                    _brightnessOverride.StopOverride();
                    _logger?.LogInformation("Brightness override stopped.");
                }
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Error stopping brightness override.");
            }
        }
    }
}
