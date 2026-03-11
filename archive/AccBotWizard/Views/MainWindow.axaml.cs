using Avalonia.Controls;
using Avalonia.Data.Converters;
using Avalonia.Media;
using System;
using System.Collections.Generic;
using System.Globalization;

namespace AccBotWizard.Views;

public partial class MainWindow : Window
{
    public static readonly IValueConverter StepBackgroundConverter = new StepBackgroundValueConverter();

    public MainWindow()
    {
        InitializeComponent();
    }

    private class StepBackgroundValueConverter : IValueConverter
    {
        public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        {
            if (value is int currentStep && parameter is string stepName)
            {
                var steps = new List<string>
                {
                    "Welcome", "Select Exchange", "API Credentials",
                    "DCA Configuration", "Telegram Setup", "Deployment", "Review & Deploy"
                };

                var stepIndex = steps.IndexOf(stepName);
                if (stepIndex < currentStep)
                    return new SolidColorBrush(Color.FromArgb(100, 255, 255, 255)); // Completed
                if (stepIndex == currentStep)
                    return new SolidColorBrush(Color.FromArgb(200, 255, 255, 255)); // Current
                return new SolidColorBrush(Color.FromArgb(50, 255, 255, 255)); // Future
            }
            return new SolidColorBrush(Color.FromArgb(50, 255, 255, 255));
        }

        public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
        {
            throw new NotImplementedException();
        }
    }
}
