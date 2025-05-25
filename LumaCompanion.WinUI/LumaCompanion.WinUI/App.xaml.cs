using LumaCompanion.WinUI.Helpers;
using LumaCompanion.WinUI.Services; // For service interfaces and implementations
using LumaCompanion.WinUI.Views;
using Microsoft.Extensions.Configuration; // For IConfiguration
using Microsoft.Extensions.Logging; // For ILogger (optional for now)
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using System;
using System.Net.Http; // For HttpClient
using System.Threading.Tasks;
using Windows.ApplicationModel;

namespace LumaCompanion.WinUI
{
    public partial class App : Application
    {
        private Window _window;
        public static MainWindow AppMainWindow { get; private set; }
        
        // Configuration and Services
        public static IConfiguration Configuration { get; private set; }
        public static HttpClient HttpClientInstance { get; private set; }
        public static ISettingsService SettingsSvc { get; private set; }
        public static IConversationLogService ConversationLogSvc { get; private set; }
        public static ISpeechRecognitionService SpeechRecognitionSvc { get; private set; }
        public static IOpenRouterService OpenRouterSvc { get; private set; }
        public static IOpenAiService OpenAiSvc { get; private set; }
        public static ITtsService TtsSvc { get; private set; }
        public static DisplayManager DisplayMgr { get; private set; }
        // public static ILogger<App> AppLogger { get; private set; } // Optional

        public App()
        {
            this.InitializeComponent();

            // Setup Configuration
            var builder = new ConfigurationBuilder()
                .SetBasePath(System.AppContext.BaseDirectory)
                .AddJsonFile("appsettings.json", optional: true, reloadOnChange: true)
                .AddUserSecrets<App>(); // Assumes UserSecretsId is in csproj for LumaCompanion.WinUI
            Configuration = builder.Build();

            // Setup Static Services (Basic Manual DI)
            // Logger can be properly set up here if a logging framework is added
            // For now, passing null for loggers to services.
            // AppLogger = null; // Initialize your main app logger here

            HttpClientInstance = new HttpClient();
            
            // Services that don't depend on IConfiguration or HttpClient directly for construction
            // (but might use them internally if passed to methods or if they were full DI components)
            SettingsSvc = new SettingsService(null); 
            ConversationLogSvc = new ConversationLogService(null);
            SpeechRecognitionSvc = new WindowsSpeechRecognitionService(null);
            DisplayMgr = new DisplayManager(null);

            // Services that depend on IConfiguration and/or HttpClient
            OpenRouterSvc = new OpenRouterService(HttpClientInstance, Configuration, null);
            OpenAiSvc = new OpenAiService(HttpClientInstance, Configuration, null);
            TtsSvc = new OpenAiTtsService(HttpClientInstance, Configuration, null);


            this.Suspending += OnSuspending;
            this.Resuming += OnResuming;
            // this.UnhandledException += App_UnhandledException;
        }

        protected override async void OnLaunched(LaunchActivatedEventArgs e)
        {
            // AppLogger?.LogInformation("Application OnLaunched.");

            DisplayMgr.ActivateDisplay();
            await DisplayMgr.AdjustBrightnessForTimeOfDayAsync();

            // Ensure the _window field is assigned.
            // The XAML template might implicitly create a window, but managing it explicitly is better.
            // _window = Window.Current; // This can be problematic.
            // Let's ensure MainWindow is our main window.
            
            if (AppMainWindow == null) // Check if MainWindow is already created
            {
                _window = new MainWindow();
                AppMainWindow = _window as MainWindow;
            }
            else
            {
                _window = AppMainWindow;
            }


            Frame rootFrame = AppMainWindow.GetRootFrame();
            if (rootFrame == null)
            {
                // This path should ideally not be hit if MainWindow creates its RootFrame correctly.
                // If it does, it means MainWindow's content setup is flawed.
                // For safety, one might re-initialize or throw.
                // For now, assume GetRootFrame() in MainWindow is reliable.
                throw new Exception("RootFrame not found in AppMainWindow.");
            }
            rootFrame.NavigationFailed += OnNavigationFailed;
            
            // Initial navigation is handled in MainWindow.xaml.cs Loaded event.
            _window.Activate();
        }

        private async void OnResuming(object sender, object e)
        {
            // AppLogger?.LogInformation("Application Resuming.");
            DisplayMgr.ActivateDisplay();
            await DisplayMgr.AdjustBrightnessForTimeOfDayAsync();
        }

        private void OnSuspending(object sender, SuspendingEventArgs e)
        {
            // AppLogger?.LogInformation("Application Suspending.");
            var deferral = e.SuspendingOperation.GetDeferral();
            try
            {
                DisplayMgr.ReleaseDisplay();
                DisplayMgr.StopBrightnessOverride();
            }
            catch (Exception ex)
            {
                // AppLogger?.LogError(ex, "Error during OnSuspending cleanup.");
            }
            finally
            {
                deferral.Complete();
            }
        }

        void OnNavigationFailed(object sender, NavigationFailedEventArgs e)
        {
            // AppLogger?.LogError($"Navigation failed for page {e.SourcePageType.FullName}: {e.Exception}");
            throw new Exception("Failed to load Page " + e.SourcePageType.FullName + " - " + e.Exception.Message);
        }

        // private void App_UnhandledException(object sender, Microsoft.UI.Xaml.UnhandledExceptionEventArgs e)
        // {
        //     AppLogger?.LogCritical(e.Exception, $"Application Unhandled Exception: {e.Message}");
        //     e.Handled = true; 
        // }
    }
}
