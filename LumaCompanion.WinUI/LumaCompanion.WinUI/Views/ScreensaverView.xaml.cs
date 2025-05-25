using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media.Animation; 
using Microsoft.UI.Xaml.Navigation; 


namespace LumaCompanion.WinUI.Views
{
    public sealed partial class ScreensaverView : Page
    {
        private DispatcherTimer _clockTimer;
        private DispatcherTimer _phraseTimer;
        private List<string> _phrases = new List<string>
        {
            "Hi, I'm Luma.",
            "Tap to talk with me.",
            "How can I help you today?",
            "Ask me anything!",
            "Let's explore together."
        };
        private int _currentPhraseIndex = 0;

        public ScreensaverView()
        {
            this.InitializeComponent();
            SetupClockTimer();
            SetupPhraseTimer();
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);
            BackgroundAnimation1?.Begin();
            BackgroundAnimation2?.Begin();
            _clockTimer.Start();
            _phraseTimer.Start();
            UpdateDateTime(); 
            CyclePhrase(); 
        }

        protected override void OnNavigatedFrom(NavigationEventArgs e)
        {
            base.OnNavigatedFrom(e);
            _clockTimer.Stop();
            _phraseTimer.Stop();
            BackgroundAnimation1?.Stop();
            BackgroundAnimation2?.Stop();
        }

        private void SetupClockTimer()
        {
            _clockTimer = new DispatcherTimer();
            _clockTimer.Interval = TimeSpan.FromSeconds(1);
            _clockTimer.Tick += ClockTimer_Tick;
        }

        private void ClockTimer_Tick(object sender, object e)
        {
            UpdateDateTime();
        }

        private void UpdateDateTime()
        {
            var now = DateTime.Now;
            TimeTextBlock.Text = now.ToString("h:mm tt", CultureInfo.InvariantCulture).ToUpper(); 
            DateTextBlock.Text = now.ToString("dddd, MMMM d", CultureInfo.InvariantCulture); 
        }

        private void SetupPhraseTimer()
        {
            _phraseTimer = new DispatcherTimer();
            _phraseTimer.Interval = TimeSpan.FromSeconds(12); 
            _phraseTimer.Tick += PhraseTimer_Tick;
        }

        private async void PhraseTimer_Tick(object sender, object e)
        {
            await AnimatePhraseChange();
        }
        
        private async System.Threading.Tasks.Task AnimatePhraseChange()
        {
            if (TextFadeOutAnimation != null && TextFadeInAnimation != null)
            {
                // Ensure previous animation is stopped before re-attaching completed handler
                // or use a flag to prevent re-entry if not strictly necessary with how Begin works.
                // For simplicity, assuming Begin restarts and Completed fires once per Begin.
                TextFadeOutAnimation.Completed -= FadeOut_Completed; // Remove previous handler if any
                TextFadeOutAnimation.Completed += FadeOut_Completed;
                TextFadeOutAnimation.Begin();
            }
            else 
            {
                CyclePhrase();
            }
            await System.Threading.Tasks.Task.CompletedTask;
        }

        private void FadeOut_Completed(object sender, object e)
        {
            // Unsubscribe to prevent multiple triggers if animation system behaves unexpectedly.
            TextFadeOutAnimation.Completed -= FadeOut_Completed;
            CyclePhrase();
            TextFadeInAnimation?.Begin();
        }

        private void CyclePhrase()
        {
            _currentPhraseIndex = (_currentPhraseIndex + 1) % _phrases.Count;
            CyclingPhraseTextBlock.Text = _phrases[_currentPhraseIndex];
        }

        private void Grid_PointerPressed(object sender, PointerRoutedEventArgs e)
        {
            // Navigate to InteractionView using App.AppMainWindow
            if (App.AppMainWindow != null)
            {
                 App.AppMainWindow.NavigateToInteraction();
            }
            // else: Log error or handle case where AppMainWindow is unexpectedly null
        }

        private async void SettingsButton_Click(object sender, RoutedEventArgs e)
        {
            SettingsDialog settingsDialog = new SettingsDialog();
            settingsDialog.XamlRoot = this.XamlRoot; 
            
            // SettingsDialog now loads its settings internally using App.SettingsSvc
            await settingsDialog.ShowAsync();

            // If InteractionViewModel needs to be aware of settings changes immediately,
            // some notification mechanism or re-load in InteractionViewModel would be needed.
            // For now, InteractionViewModel reloads settings on its LoadAsync.
        }
    }
}
