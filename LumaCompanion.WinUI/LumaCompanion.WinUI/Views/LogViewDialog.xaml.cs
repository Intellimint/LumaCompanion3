using LumaCompanion.WinUI.Models; 
using LumaCompanion.WinUI.Services; 
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Data; 
using System;
using System.Collections.ObjectModel;
using System.Globalization; 
using System.Linq; 

namespace LumaCompanion.WinUI.Views
{
    public sealed partial class LogViewDialog : ContentDialog
    {
        public ObservableCollection<LogEntry> LogEntries { get; set; }
        private readonly IConversationLogService _logService;
        // private readonly ILogger<LogViewDialog> _logger; // Optional

        public LogViewDialog() 
        {
            this.InitializeComponent();
            
            // Obtain ConversationLogService from App.xaml.cs static property
            _logService = App.ConversationLogSvc ?? throw new NullReferenceException("ConversationLogService not initialized in App.xaml.cs");
            // _logger = App.AppLoggerProvider?.CreateLogger<LogViewDialog>(); // If a logger provider is set up

            LogEntries = new ObservableCollection<LogEntry>();
            LogListView.ItemsSource = LogEntries;
            this.Loaded += LogViewDialog_Loaded;
        }

        private async void LogViewDialog_Loaded(object sender, RoutedEventArgs e)
        {
            try
            {
                var loadedEntries = await _logService.LoadEntriesAsync();
                if (loadedEntries != null && loadedEntries.Any())
                {
                    LogEntries.Clear();
                    foreach (var entry in loadedEntries) 
                    {
                        LogEntries.Add(entry);
                    }
                    NoLogsTextBlock.Visibility = Visibility.Collapsed;
                    LogListView.Visibility = Visibility.Visible;
                }
                else
                {
                    NoLogsTextBlock.Visibility = Visibility.Visible;
                    LogListView.Visibility = Visibility.Collapsed;
                    // _logger?.LogInformation("No log entries found or loaded list was empty.");
                }
            }
            catch (Exception ex)
            {
                // _logger?.LogError(ex, "Failed to load log entries.");
                NoLogsTextBlock.Text = "Error loading logs.";
                NoLogsTextBlock.Visibility = Visibility.Visible;
                LogListView.Visibility = Visibility.Collapsed;
            }
        }
    }

    // Converter for formatting DateTime in XAML (already defined in the previous version of this file)
    // Ensure it's still here if it was part of the original LogViewDialog.xaml.cs
    // If it was moved to a separate file, this redundant definition should be removed.
    // For this task, assuming it was part of this file.
    public class DateTimeFormatConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, string language)
        {
            if (value is DateTime dateTime)
            {
                string format = parameter as string;
                if (!string.IsNullOrEmpty(format))
                {
                    return dateTime.ToString(format, CultureInfo.InvariantCulture);
                }
                return dateTime.ToString(CultureInfo.CurrentUICulture); 
            }
            return value;
        }

        public object ConvertBack(object value, Type targetType, object parameter, string language)
        {
            throw new NotImplementedException(); 
        }
    }
}
