using LumaCompanion.WinUI.ViewModels;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input; 
using Microsoft.UI.Xaml.Navigation;
// No need for System.Collections.ObjectModel here

namespace LumaCompanion.WinUI.Views
{
    public sealed partial class InteractionView : Page
    {
        // ViewModel is now set in XAML via <Page.DataContext>
        // public InteractionViewModel ViewModel { get; private set; }

        // Helper to access the ViewModel typed
        public InteractionViewModel ViewModel => DataContext as InteractionViewModel;


        public InteractionView()
        {
            this.InitializeComponent();
            // DataContext is set in XAML: <Page.DataContext><viewmodels:InteractionViewModel x:Name="ViewModel"/></Page.DataContext>
            // However, the ViewModel created by XAML will use its default constructor.
            // We need to replace it with one that has services injected.
            // This is a common issue when not using a full DI framework with View-ViewModel location.
            // A better approach would be to set DataContext here after creating VM with dependencies.
            // For now, we will overwrite the DataContext.
            
            // Ensure services are available from App.xaml.cs
            if (App.SpeechRecognitionSvc == null)
            {
                // This indicates a severe initialization problem in App.xaml.cs
                // Or that InteractionView is being created before App.xaml.cs has fully initialized services.
                // Handle this error appropriately, perhaps by throwing or logging.
                // For now, we'll let it proceed, and it will likely fail in ViewModel constructor if services are null.
                // A more robust app might have a splash screen or delay view creation until services are ready.
                System.Diagnostics.Debug.WriteLine("Error: Services not initialized in App.xaml.cs before InteractionView creation.");
            }
            
            // Replace the XAML-instantiated ViewModel with one that has dependencies.
            // Note: The x:Name="ViewModel" in XAML will still refer to the XAML-instantiated one,
            // which is not ideal. Bindings should ideally use {x:Bind ViewModel.Property} where ViewModel
            // is the public property in this code-behind, which we then set.
            var vm = new InteractionViewModel(
                App.SpeechRecognitionSvc,
                App.TtsSvc,
                App.OpenRouterSvc,
                App.OpenAiSvc,
                App.SettingsSvc,
                App.ConversationLogSvc,
                null // Pass ILogger<InteractionViewModel> if available from App.xaml.cs
            );
            this.DataContext = vm; // Set the DataContext to the properly initialized ViewModel instance.
        }

        protected override async void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);
            if (ViewModel != null) // ViewModel is now from DataContext
            {
                await ViewModel.LoadAsync();
            }
        }

        protected override async void OnNavigatedFrom(NavigationEventArgs e)
        {
            base.OnNavigatedFrom(e);
            if (ViewModel != null) // ViewModel is now from DataContext
            {
                 await ViewModel.UnloadAsync();
            }
        }
        
        private async void WaveformPlaceholder_Tapped(object sender, TappedRoutedEventArgs e)
        {
            if (ViewModel != null) // ViewModel is now from DataContext
            {
                await ViewModel.StartListeningAsync();
            }
        }

        private async void SettingsButton_Click(object sender, RoutedEventArgs e)
        {
            SettingsDialog settingsDialog = new SettingsDialog(); 
            settingsDialog.XamlRoot = this.XamlRoot;
            
            // SettingsDialog now uses App.SettingsSvc internally, so no need to pass settings object around from here.
            // If settingsDialog needs to be re-populated upon showing (e.g. if settings could change elsewhere),
            // the SettingsDialog.Loaded event should handle calling its internal LoadPersonalizationAsync again.
            
            await settingsDialog.ShowAsync();

            // After dialog closes, if settings changes might affect InteractionViewModel,
            // the ViewModel might need a method to refresh its settings.
            // For example: await ViewModel.RefreshSettingsAsync();
            // This depends on what PersonalizationSettings affect in InteractionViewModel.
            // For now, _personalizationSettings in InteractionViewModel is loaded at LoadAsync.
            // If settings change (e.g. user name), they won't reflect in system prompts until next LoadAsync (e.g. app restart).
            // To make it dynamic, InteractionViewModel would need to reload or be notified of settings changes.
             if (ViewModel != null)
             {
                await ViewModel.LoadAsync(); // Reload settings after dialog closes, this will re-fetch personalization.
             }
        }

        private void CloseButton_Click(object sender, RoutedEventArgs e)
        {
            // Use App.AppMainWindow for navigation as established.
            if (App.AppMainWindow != null)
            {
                 App.AppMainWindow.NavigateToScreensaver();
            }
            // Fallback if App.AppMainWindow is somehow null (should not happen)
            // else if (Window.Current.Content is Frame rootFrame)
            // {
            //    rootFrame.Navigate(typeof(ScreensaverView));
            // }
        }
    }
}
