using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media;
using System.ComponentModel; // Required for INotifyPropertyChanged
// No need for Microsoft.UI.Colors if using App.xaml resources directly by key

namespace LumaCompanion.WinUI.ViewModels
{
    public class ChatMessageViewModel : INotifyPropertyChanged
    {
        private string _role;
        public string Role
        {
            get => _role;
            set { _role = value; OnPropertyChanged(nameof(Role)); OnPropertyChanged(nameof(DisplayMessage)); UpdateAlignmentAndColor(); }
        }

        private string _content;
        public string Content
        {
            get => _content;
            set { _content = value; OnPropertyChanged(nameof(Content)); OnPropertyChanged(nameof(DisplayMessage)); }
        }

        public string DisplayMessage => Role == "User" ? $"You: {Content}" : $"Luma: {Content}";

        private HorizontalAlignment _horizontalAlignment;
        public HorizontalAlignment HorizontalAlignment
        {
            get => _horizontalAlignment;
            private set { _horizontalAlignment = value; OnPropertyChanged(nameof(HorizontalAlignment)); }
        }

        private SolidColorBrush _bubbleColor;
        public SolidColorBrush BubbleColor
        {
            get => _bubbleColor;
            private set { _bubbleColor = value; OnPropertyChanged(nameof(BubbleColor)); }
        }
        
        // Constructor
        public ChatMessageViewModel(string role, string content)
        {
            _role = role; 
            _content = content;
            UpdateAlignmentAndColor();
        }

        private void UpdateAlignmentAndColor()
        {
            // Attempt to get brushes from Application resources
            // This assumes the ViewModel is instantiated on the UI thread or has access to Application.Current.Resources
            // For robustness, especially if view models are on background threads or in separate libraries,
            // passing brushes or resource keys might be preferred.
            if (Role == "User")
            {
                HorizontalAlignment = HorizontalAlignment.Right;
                if (Application.Current.Resources.TryGetValue("LumaOrangeBrush", out var brush))
                {
                    BubbleColor = brush as SolidColorBrush;
                }
                else
                {
                    BubbleColor = new SolidColorBrush(Microsoft.UI.Colors.Orange); // Fallback
                }
            }
            else // Luma
            {
                HorizontalAlignment = HorizontalAlignment.Left;
                if (Application.Current.Resources.TryGetValue("LumaLightGreyBrush", out var brush))
                {
                    BubbleColor = brush as SolidColorBrush;
                }
                else
                {
                     BubbleColor = new SolidColorBrush(Microsoft.UI.Colors.DarkGray); // Fallback
                }
            }
            OnPropertyChanged(nameof(DisplayMessage));
        }

        public event PropertyChangedEventHandler PropertyChanged;
        protected virtual void OnPropertyChanged(string propertyName)
        {
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
        }
    }
}
