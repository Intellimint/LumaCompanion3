using LumaCompanion.WinUI.Models;
using LumaCompanion.WinUI.Services; 
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using System; 
using System.Linq; // Added for LINQ operations like FirstOrDefault

namespace LumaCompanion.WinUI.Views
{
    public sealed partial class SettingsDialog : ContentDialog
    {
        public PersonalizationSettings Settings { get; private set; }
        private readonly ISettingsService _settingsService;
        // private readonly ILogger<SettingsDialog> _logger; // Optional logger

        public SettingsDialog() 
        {
            this.InitializeComponent();
            
            // Obtain SettingsService from App.xaml.cs static property
            _settingsService = App.SettingsSvc ?? throw new NullReferenceException("SettingsService not initialized in App.xaml.cs");
            // _logger = App.AppLoggerProvider?.CreateLogger<SettingsDialog>(); // If a logger provider is set up

            Settings = new PersonalizationSettings(); 
            
            this.Loaded += SettingsDialog_Loaded;
            this.PrimaryButtonClick += SettingsDialog_PrimaryButtonClick;
            PronounsComboBox.SelectionChanged += PronounsComboBox_SelectionChanged;
        }

        private async void SettingsDialog_Loaded(object sender, RoutedEventArgs e)
        {
            try
            {
                Settings = await _settingsService.LoadPersonalizationAsync();
                if (Settings == null)
                {
                    // _logger?.LogWarning("Loaded settings were null, re-initializing.");
                    Settings = new PersonalizationSettings();
                }
            }
            catch (Exception ex)
            {
                // _logger?.LogError(ex, "Failed to load personalization settings. Using defaults.");
                Settings = new PersonalizationSettings(); 
            }
            PopulateControlsFromSettings();
        }

        private void PopulateControlsFromSettings()
        {
            NameTextBox.Text = Settings.Name ?? "";
            ReligionTextBox.Text = Settings.Religion ?? "";

            if (!string.IsNullOrEmpty(Settings.Pronouns))
            {
                var matchingItem = PronounsComboBox.Items.FirstOrDefault(item => 
                    item is string s && s.Equals(Settings.Pronouns, StringComparison.OrdinalIgnoreCase));
                
                if (matchingItem != null)
                {
                    PronounsComboBox.SelectedItem = matchingItem;
                }
                else if (Settings.Pronouns.Equals("Custom", StringComparison.OrdinalIgnoreCase) && !string.IsNullOrEmpty(Settings.PronounsCustom))
                {
                    PronounsComboBox.SelectedItem = PronounsComboBox.Items.OfType<string>().FirstOrDefault(s => s.Equals("Custom", StringComparison.OrdinalIgnoreCase));
                    CustomPronounsTextBox.Text = Settings.PronounsCustom ?? "";
                }
                else if (!string.IsNullOrEmpty(Settings.Pronouns)) 
                {
                    PronounsComboBox.SelectedItem = PronounsComboBox.Items.OfType<string>().FirstOrDefault(s => s.Equals("Custom", StringComparison.OrdinalIgnoreCase));
                    CustomPronounsTextBox.Text = Settings.Pronouns; 
                }
            }
            else
            {
                PronounsComboBox.SelectedIndex = -1; 
            }
            UpdateCustomPronounsVisibility();
        }

        private void PronounsComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            UpdateCustomPronounsVisibility();
        }

        private void UpdateCustomPronounsVisibility()
        {
            if (PronounsComboBox.SelectedItem is string selectedPronoun && selectedPronoun.Equals("Custom", StringComparison.OrdinalIgnoreCase))
            {
                CustomPronounsTextBox.Visibility = Visibility.Visible;
            }
            else
            {
                CustomPronounsTextBox.Visibility = Visibility.Collapsed;
                // CustomPronounsTextBox.Text = string.Empty; // Keep text if user toggles, cleared on save if "Custom" not selected
            }
        }
        
        private async void SettingsDialog_PrimaryButtonClick(ContentDialog sender, ContentDialogButtonClickEventArgs args)
        {
            var deferral = args.GetDeferral(); 
            try
            {
                UpdateSettingsFromControls();
                await _settingsService.SavePersonalizationAsync(Settings);
                // _logger?.LogInformation("Settings saved successfully.");
            }
            catch (Exception ex)
            {
                // _logger?.LogError(ex, "Failed to save settings.");
                // args.Cancel = true; // Optionally keep dialog open on error
            }
            finally
            {
                deferral.Complete();
            }
        }

        private void UpdateSettingsFromControls()
        {
            Settings.Name = NameTextBox.Text.Trim();
            Settings.Religion = ReligionTextBox.Text.Trim();

            if (PronounsComboBox.SelectedItem is string selectedPronoun)
            {
                Settings.Pronouns = selectedPronoun;
                if (selectedPronoun.Equals("Custom", StringComparison.OrdinalIgnoreCase))
                {
                    Settings.PronounsCustom = CustomPronounsTextBox.Text.Trim();
                }
                else
                {
                    Settings.PronounsCustom = string.Empty;
                }
            }
            else
            {
                Settings.Pronouns = string.Empty; 
                Settings.PronounsCustom = string.Empty;
            }
        }
    }
}
