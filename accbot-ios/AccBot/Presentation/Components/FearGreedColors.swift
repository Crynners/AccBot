import SwiftUI

/// Shared Fear & Greed color mapping. Single source of truth for gauge segments and indicator colors.
enum FearGreedColors {
    static func color(for value: Int) -> Color {
        switch value {
        case 0...19: return gaugeColors[0]
        case 20...39: return gaugeColors[1]
        case 40...59: return gaugeColors[2]
        case 60...79: return gaugeColors[3]
        default: return gaugeColors[4]
        }
    }

    /// The 5 gauge segment colors in order (extreme fear → extreme greed)
    static let gaugeColors: [Color] = [
        Color(red: 0xE5/255, green: 0x39/255, blue: 0x35/255),  // Red
        Color(red: 0xFF/255, green: 0x98/255, blue: 0x00/255),  // Orange
        Color(red: 0xFD/255, green: 0xD8/255, blue: 0x35/255),  // Yellow
        Color(red: 0x8B/255, green: 0xC3/255, blue: 0x4A/255),  // Light Green
        Color(red: 0x4C/255, green: 0xAF/255, blue: 0x50/255),  // Green
    ]
}
