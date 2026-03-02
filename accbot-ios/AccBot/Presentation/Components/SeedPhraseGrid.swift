import SwiftUI

/// Reusable 12-word seed phrase input grid with autocomplete.
struct SeedPhraseGrid: View {
    @Binding var words: [String]
    let getSuggestions: (String) -> [String]
    let isValidWord: (String) -> Bool
    let readOnly: Bool

    @State private var focusedIndex: Int? = nil
    @State private var suggestions: [String] = []
    @FocusState private var focusedField: Int?

    @Environment(\.accBotColors) private var colors

    init(
        words: Binding<[String]>,
        getSuggestions: @escaping (String) -> [String] = { _ in [] },
        isValidWord: @escaping (String) -> Bool = { _ in true },
        readOnly: Bool = false
    ) {
        self._words = words
        self.getSuggestions = getSuggestions
        self.isValidWord = isValidWord
        self.readOnly = readOnly
    }

    var body: some View {
        let rows = Int(ceil(Double(words.count) / 3.0))
        VStack(spacing: Spacing.sm) {
            ForEach(0..<rows, id: \.self) { row in
                HStack(spacing: Spacing.sm) {
                    ForEach(0..<3, id: \.self) { col in
                        let index = row * 3 + col
                        if index < words.count {
                            wordCell(index: index)
                        }
                    }
                }
            }

            // Suggestions row
            if !suggestions.isEmpty && !readOnly {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Spacing.sm) {
                        ForEach(suggestions, id: \.self) { suggestion in
                            Button(suggestion) {
                                if let idx = focusedIndex {
                                    words[idx] = suggestion
                                    suggestions = []
                                    // Advance focus
                                    if idx < 11 {
                                        focusedIndex = idx + 1
                                        focusedField = idx + 1
                                    } else {
                                        focusedIndex = nil
                                        focusedField = nil
                                    }
                                }
                            }
                            .accessibilityLabel(String(localized: "Suggestion: \(suggestion)"))
                            .font(AccBotFonts.bodySmall)
                            .padding(.horizontal, Spacing.md)
                            .padding(.vertical, Spacing.xs)
                            .frame(minHeight: 44)
                            .background(colors.surfaceVariant)
                            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                            .foregroundStyle(colors.onSurface)
                        }
                    }
                    .padding(.horizontal, Spacing.xs)
                }
            }
        }
    }

    private func wordCell(index: Int) -> some View {
        let word = index < words.count ? words[index] : ""
        let isValid = word.isEmpty || isValidWord(word)
        let isFocused = focusedIndex == index

        return VStack(spacing: 2) {
            HStack(spacing: 4) {
                Text("\(index + 1).")
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .frame(width: 20)

                if readOnly {
                    Text(word)
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.onSurface)
                        .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    TextField("", text: Binding(
                        get: { words[index] },
                        set: { newValue in
                            // Handle paste: detect multiple words
                            let cleaned = newValue.lowercased()
                            if cleaned.contains(" ") || cleaned.contains("\n") {
                                let pastedWords = cleaned.components(separatedBy: .whitespacesAndNewlines)
                                    .filter { !$0.isEmpty }
                                for (i, w) in pastedWords.prefix(12).enumerated() {
                                    if index + i < 12 { words[index + i] = w }
                                }
                                let pastedCount = min(pastedWords.count, 12 - index)
                                let nextIdx = min(index + pastedWords.count, 11)
                                focusedIndex = nextIdx
                                focusedField = nextIdx
                                suggestions = []
                                UIAccessibility.post(
                                    notification: .announcement,
                                    argument: String(localized: "Pasted \(pastedCount) words")
                                )
                            } else {
                                words[index] = cleaned
                                suggestions = getSuggestions(cleaned)
                            }
                        }
                    ))
                    .font(AccBotFonts.bodySmall)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .focused($focusedField, equals: index)
                    .onTapGesture {
                        focusedIndex = index
                        focusedField = index
                    }
                    .onChange(of: focusedField) { newVal in
                        focusedIndex = newVal
                        if let idx = newVal {
                            suggestions = getSuggestions(words[idx])
                        }
                    }
                }
            }
            .padding(.horizontal, Spacing.sm)
            .padding(.vertical, Spacing.sm)
            .frame(minWidth: 44, minHeight: 44)
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
            .overlay(
                RoundedRectangle(cornerRadius: CornerRadius.sm)
                    .strokeBorder(
                        isFocused ? colors.primary :
                        (!isValid ? colors.error :
                        (!word.isEmpty && isValid ? colors.primary.opacity(0.4) : Color.clear)),
                        lineWidth: 1
                    )
            )
            .accessibilityLabel(String(localized: "Word \(index + 1)"))
            .accessibilityValue(word.isEmpty ? String(localized: "Empty") : word)
            .accessibilityHint(!isValid && !readOnly ? String(localized: "Invalid word") : "")

            if !isValid && !readOnly {
                Text(String(localized: "Invalid word"))
                    .font(AccBotFonts.captionSmall)
                    .foregroundStyle(colors.error)
            }
        }
    }
}

/// Read-only seed phrase display (for export confirmation)
struct SeedPhraseDisplay: View {
    let words: [String]

    @Environment(\.accBotColors) private var colors

    var body: some View {
        let rows = Int(ceil(Double(words.count) / 3.0))
        VStack(spacing: Spacing.sm) {
            ForEach(0..<rows, id: \.self) { row in
                HStack(spacing: Spacing.sm) {
                    ForEach(0..<3, id: \.self) { col in
                        let index = row * 3 + col
                        if index < words.count {
                            HStack(spacing: 4) {
                                Text("\(index + 1).")
                                    .font(AccBotFonts.caption)
                                    .foregroundStyle(colors.onSurfaceVariant)
                                    .frame(width: 20)
                                Text(words[index])
                                    .font(.system(.body, design: .monospaced))
                                    .foregroundStyle(colors.onSurface)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .privacySensitive()
                            }
                            .padding(.horizontal, Spacing.sm)
                            .padding(.vertical, Spacing.sm)
                            .background(colors.surface)
                            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                            .accessibilityElement(children: .combine)
                            .accessibilityLabel(String(localized: "Word \(index + 1): \(words[index])"))
                        }
                    }
                }
            }
        }
    }
}
