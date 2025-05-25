using LumaCompanion.WinUI.Views;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Windowing; // Required for AppWindow and PresenterKind
using Windows.System; // Required for VirtualKey
using WinRT.Interop; // Required for WindowNative and Win32Interop
using System; // Required for IntPtr

namespace LumaCompanion.WinUI
{
    public sealed partial class MainWindow : Window
    {
        private AppWindow _appWindow;
        private bool _isFullscreen = false;

        public MainWindow()
        {
            this.InitializeComponent();
            this.Title = "Luma Companion";
            this.Loaded += MainWindow_Loaded;

            _appWindow = GetAppWindowForCurrentWindow();

            // Set initial full-screen state
            if (_appWindow != null)
            {
                _appWindow.SetPresenter(AppWindowPresenterKind.FullScreen);
                _isFullscreen = true;
            }

            // Register for key events to handle F11 toggle
            // Note: For CoreWindow KeyDown, it's typically done via CoreWindow.GetForCurrentThread().KeyDown
            // However, for a WinUI 3 Desktop Window, direct XAML keyboard events are more straightforward if available
            // or using a general keyboard hook if necessary. For simplicity, we'll try a XAML-level event.
            // If RootFrame is the primary content, its KeyDown might be suitable.
            // Let's attach it to the window itself if possible, or a main content element.
            // The most reliable way in WinUI 3 Desktop for global-like key presses on the window
            // often involves more complex event handling or ensuring focus.
            // For this task, we'll attach KeyDown to the RootFrame.
            // It's better to attach it after RootFrame is initialized.
        }

        private void MainWindow_Loaded(object sender, RoutedEventArgs e)
        {
            NavigateToScreensaver();
            // Ensure RootFrame is available before attaching KeyDown
            if (RootFrame != null)
            {
                RootFrame.KeyDown += RootFrame_KeyDown;
                // Also important to ensure RootFrame can receive focus to get KeyDown events.
                RootFrame.IsTabStop = true; 
                RootFrame.Focus(FocusState.Programmatic);
            }
        }
        
        private Microsoft.UI.Windowing.AppWindow GetAppWindowForCurrentWindow()
        {
            IntPtr hWnd = WindowNative.GetWindowHandle(this); 
            WindowId wndId = Win32Interop.GetWindowIdFromWindow(hWnd);
            return AppWindow.GetFromWindowId(wndId);
        }

        private void RootFrame_KeyDown(object sender, Microsoft.UI.Xaml.Input.KeyRoutedEventArgs e)
        {
            if (e.Key == VirtualKey.F11)
            {
                ToggleFullScreen();
            }
        }

        public void ToggleFullScreen()
        {
            if (_appWindow == null) return;

            if (_isFullscreen)
            {
                _appWindow.SetPresenter(AppWindowPresenterKind.Default); // Or Overlapped
            }
            else
            {
                _appWindow.SetPresenter(AppWindowPresenterKind.FullScreen);
            }
            _isFullscreen = !_isFullscreen;
        }

        public Frame GetRootFrame()
        {
            return RootFrame;
        }

        public void NavigateToScreensaver()
        {
            RootFrame.Navigate(typeof(ScreensaverView));
        }

        public void NavigateToInteraction()
        {
            RootFrame.Navigate(typeof(InteractionView));
        }
    }
}
