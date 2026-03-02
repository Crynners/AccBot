import SwiftUI
import UniformTypeIdentifiers

struct ImportCsvView: View {
    let planId: Int64
    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @StateObject private var viewModel: ImportCsvViewModel
    @Environment(\.dismiss) var dismiss
    @Environment(\.accBotColors) private var colors
    @State private var showFilePicker = false

    init(planId: Int64) {
        self.planId = planId
        _viewModel = StateObject(wrappedValue: ImportCsvViewModel(planId: planId))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.lg) {
                if let plan = viewModel.plan {
                    // Plan info
                    HStack {
                        Text(plan.pair)
                            .font(AccBotFonts.titleSmall)
                            .foregroundStyle(colors.onSurface)
                        Text(plan.exchange.displayName)
                            .font(AccBotFonts.bodySmall)
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                }

                // Import mode selector
                if viewModel.plan?.exchange.supportsApiImport == true {
                    Picker(String(localized: "Import Mode"), selection: $viewModel.importMode) {
                        ForEach(ImportCsvViewModel.ImportMode.allCases, id: \.self) { mode in
                            Text(mode.localizedName).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                if viewModel.isComplete {
                    completionView
                } else if viewModel.isImporting {
                    progressView
                } else if viewModel.isPreviewing {
                    previewView
                } else {
                    importActionView
                }

                if let error = viewModel.errorMessage {
                    Text(error)
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.error)
                        .padding(Spacing.md)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(colors.error.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.top, Spacing.lg)
            .maxFormWidth()
        }
        .background(colors.background)
        .navigationTitle(String(localized: "Import History"))
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { viewModel.setup(dependencies) }
        .fileImporter(
            isPresented: $showFilePicker,
            allowedContentTypes: [UTType.commaSeparatedText],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                if let url = urls.first {
                    let accessed = url.startAccessingSecurityScopedResource()
                    // Read file data synchronously while we have access
                    let csvData: String?
                    do {
                        csvData = try String(contentsOf: url, encoding: .utf8)
                    } catch {
                        csvData = nil
                    }
                    if accessed {
                        url.stopAccessingSecurityScopedResource()
                    }
                    if let data = csvData {
                        viewModel.importFromCsvData(data)
                    } else {
                        viewModel.errorMessage = String(localized: "Failed to read the selected file.")
                    }
                }
            case .failure:
                break
            }
        }
        .sheet(isPresented: $viewModel.showImportConfig) {
            ImportConfigSheet(sinceDate: $viewModel.sinceDate) {
                viewModel.importFromApi()
            }
        }
    }

    private var importActionView: some View {
        VStack(spacing: Spacing.md) {
            if viewModel.importMode == .csv {
                VStack(spacing: Spacing.sm) {
                    Image(systemName: "doc.text")
                        .font(AccBotFonts.displayLarge)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .accessibilityHidden(true)
                    Text(String(localized: "Select a CSV file to import transaction history"))
                        .font(AccBotFonts.body)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .multilineTextAlignment(.center)
                }
                .padding(.vertical, Spacing.xxl)

                Button {
                    showFilePicker = true
                } label: {
                    Label(String(localized: "Choose CSV File"), systemImage: "folder")
                        .font(AccBotFonts.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.md)
                        .background(colors.primary)
                        .foregroundStyle(colors.onPrimary)
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                }
            } else {
                VStack(spacing: Spacing.sm) {
                    Image(systemName: "arrow.down.circle")
                        .font(AccBotFonts.displayLarge)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .accessibilityHidden(true)
                    Text(String(localized: "Import trade history directly from exchange API"))
                        .font(AccBotFonts.body)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .multilineTextAlignment(.center)
                }
                .padding(.vertical, Spacing.xxl)

                Button {
                    viewModel.showImportConfig = true
                } label: {
                    Label(String(localized: "Import from API"), systemImage: "arrow.down.circle")
                        .font(AccBotFonts.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.md)
                        .background(colors.primary)
                        .foregroundStyle(colors.onPrimary)
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                }
            }
        }
    }

    private var previewView: some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: "doc.text.magnifyingglass")
                .font(AccBotFonts.displayLarge)
                .foregroundStyle(colors.primary)
                .accessibilityHidden(true)

            Text(String(localized: "Import Preview"))
                .font(AccBotFonts.titleSmall)
                .foregroundStyle(colors.onSurface)

            VStack(spacing: Spacing.sm) {
                HStack {
                    Text(String(localized: "New transactions"))
                        .font(AccBotFonts.body)
                        .foregroundStyle(colors.onSurfaceVariant)
                    Spacer()
                    Text("\(viewModel.newTransactionCount)")
                        .font(AccBotFonts.headline)
                        .foregroundStyle(colors.primary)
                }

                if viewModel.skippedTransactionCount > 0 {
                    HStack {
                        Text(String(localized: "Already exist (will be skipped)"))
                            .font(AccBotFonts.body)
                            .foregroundStyle(colors.onSurfaceVariant)
                        Spacer()
                        Text("\(viewModel.skippedTransactionCount)")
                            .font(AccBotFonts.headline)
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                }
            }
            .padding(Spacing.lg)
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))

            Button {
                viewModel.confirmImport()
            } label: {
                Text(String(localized: "Import \(viewModel.newTransactionCount) Transactions"))
                    .font(AccBotFonts.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.md)
                    .background(viewModel.newTransactionCount > 0 ? colors.primary : colors.disabledBackground)
                    .foregroundStyle(viewModel.newTransactionCount > 0 ? colors.onPrimary : colors.disabledForeground)
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
            }
            .disabled(viewModel.newTransactionCount == 0)
        }
        .padding(.vertical, Spacing.xxl)
    }

    private var progressView: some View {
        VStack(spacing: Spacing.md) {
            ProgressView(value: viewModel.progress)
                .tint(colors.primary)
                .accessibilityLabel(String(localized: "Import progress"))
                .accessibilityValue(String(localized: "\(Int(viewModel.progress * 100))%"))

            Text(String(localized: "Importing... \(viewModel.importedCount) transactions"))
                .font(AccBotFonts.body)
                .foregroundStyle(colors.onSurfaceVariant)
        }
        .padding(.vertical, Spacing.xxl)
    }

    private var completionView: some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: "checkmark.circle.fill")
                .font(AccBotFonts.displayLarge)
                .foregroundStyle(colors.primary)
                .accessibilityHidden(true)

            Text(String(localized: "Import Complete"))
                .font(AccBotFonts.titleSmall)
                .foregroundStyle(colors.onSurface)

            Text(String(localized: "\(viewModel.importedCount) transactions imported"))
                .font(AccBotFonts.body)
                .foregroundStyle(colors.onSurfaceVariant)

            Button {
                dismiss()
                router.navigate(to: .history())
            } label: {
                Label(String(localized: "View History"), systemImage: "clock.arrow.circlepath")
                    .font(AccBotFonts.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.md)
                    .background(colors.primary)
                    .foregroundStyle(colors.onPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
            }

            Button {
                dismiss()
            } label: {
                Text(String(localized: "Done"))
                    .font(AccBotFonts.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.md)
                    .background(colors.surface)
                    .foregroundStyle(colors.onSurface)
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
            }
        }
        .padding(.vertical, Spacing.xxl)
    }
}

// MARK: - Import Config Sheet

struct ImportConfigSheet: View {
    @Binding var sinceDate: Date?
    let onConfirm: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accBotColors) private var colors
    @State private var useDateFilter = false
    @State private var selectedDate = Date()

    var body: some View {
        NavigationStack {
            VStack(spacing: Spacing.lg) {
                Toggle(String(localized: "Filter by date"), isOn: $useDateFilter)
                    .padding(.horizontal, Spacing.lg)

                if useDateFilter {
                    DatePicker(
                        String(localized: "Import since"),
                        selection: $selectedDate,
                        in: ...Date(),
                        displayedComponents: .date
                    )
                    .datePickerStyle(.graphical)
                    .padding(.horizontal, Spacing.lg)
                } else {
                    VStack(spacing: Spacing.sm) {
                        Image(systemName: "clock.arrow.circlepath")
                            .font(AccBotFonts.displayLarge)
                            .foregroundStyle(colors.onSurfaceVariant)
                            .accessibilityHidden(true)
                        Text(String(localized: "All history will be imported"))
                            .font(AccBotFonts.body)
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.xxl)
                }

                Spacer()

                Button {
                    sinceDate = useDateFilter ? selectedDate : nil
                    dismiss()
                    onConfirm()
                } label: {
                    Text(String(localized: "Start Import"))
                        .font(AccBotFonts.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.md)
                        .background(colors.primary)
                        .foregroundStyle(colors.onPrimary)
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                }
                .padding(.horizontal, Spacing.lg)
            }
            .padding(.top, Spacing.lg)
            .background(colors.background)
            .navigationTitle(String(localized: "Import Configuration"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(String(localized: "Cancel")) {
                        dismiss()
                    }
                }
            }
        }
    }
}
