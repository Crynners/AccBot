import SwiftUI
import UniformTypeIdentifiers

struct BackupImportView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @StateObject private var viewModel = BackupImportViewModel()
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accBotColors) private var colors
    @State private var showFilePicker = false
    @State private var showQrScanner = false
    @State private var showReplaceConfirmation = false

    var body: some View {
        VStack(spacing: 0) {
            BackupStepIndicator(
                steps: ImportWizardStep.allCases.map { $0.title },
                currentStep: viewModel.wizardStep.rawValue
            )

            ScrollView {
                VStack(spacing: Spacing.lg) {
                    switch viewModel.wizardStep {
                    case .selectSource:
                        selectSourceStep
                    case .enterPassword:
                        enterPasswordStep
                    case .preview:
                        previewStep
                    case .result:
                        resultStep
                    }
                }
                .padding(Spacing.lg)
                .maxFormWidth()
            }

            if let error = viewModel.error {
                ErrorBanner(message: error)
                    .padding(.horizontal, Spacing.lg)
                    .padding(.bottom, Spacing.sm)
            }

            if viewModel.wizardStep == .enterPassword || viewModel.wizardStep == .preview {
                bottomBar
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .background(colors.background)
        .animation(.easeInOut(duration: 0.3), value: viewModel.wizardStep)
        .navigationTitle(String(localized: "Import Backup"))
        .navigationBarTitleDisplayMode(.inline)
        .fileImporter(
            isPresented: $showFilePicker,
            allowedContentTypes: [.json, .data],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                if let url = urls.first {
                    viewModel.handleFileSelected(url: url)
                }
            case .failure(let error):
                viewModel.error = error.localizedDescription
            }
        }
        .sheet(isPresented: $showQrScanner) {
            QrScannerSheet(title: String(localized: "Scan Backup QR")) { code in
                viewModel.handleQrScanned(content: code)
            }
        }
        .onAppear { viewModel.setup(dependencies) }
    }

    // MARK: - Step 1: Select Source

    private var selectSourceStep: some View {
        VStack(spacing: Spacing.lg) {
            Text(String(localized: "Choose how to import your backup"))
                .font(AccBotFonts.body)
                .foregroundStyle(colors.onSurfaceVariant)
                .multilineTextAlignment(.center)

            Button {
                showFilePicker = true
            } label: {
                HStack {
                    Image(systemName: "doc")
                        .font(AccBotFonts.titleSmall)
                    VStack(alignment: .leading) {
                        Text(String(localized: "Select File"))
                            .font(AccBotFonts.body)
                        Text(String(localized: "Choose a .json backup file"))
                            .font(AccBotFonts.caption)
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundStyle(colors.onSurfaceVariant)
                }
                .padding(Spacing.lg)
                .background(colors.surface)
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            }
            .foregroundStyle(colors.onSurface)

            Button {
                showQrScanner = true
            } label: {
                HStack {
                    Image(systemName: "qrcode.viewfinder")
                        .font(AccBotFonts.titleSmall)
                    VStack(alignment: .leading) {
                        Text(String(localized: "Scan QR Code"))
                            .font(AccBotFonts.body)
                        Text(String(localized: "Scan a backup QR code"))
                            .font(AccBotFonts.caption)
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundStyle(colors.onSurfaceVariant)
                }
                .padding(Spacing.lg)
                .background(colors.surface)
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            }
            .foregroundStyle(colors.onSurface)
        }
    }

    // MARK: - Step 2: Enter Password

    private var enterPasswordStep: some View {
        VStack(spacing: Spacing.lg) {
            Picker(String(localized: "Decryption method"), selection: $viewModel.inputMode) {
                Text(String(localized: "Password")).tag(EncryptionMode.password)
                Text(String(localized: "12-Word Seed")).tag(EncryptionMode.seed)
            }
            .pickerStyle(.segmented)
            .labelsHidden()

            if viewModel.inputMode == .password {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(String(localized: "Enter backup password"))
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.onSurfaceVariant)
                    SecureField(String(localized: "Password"), text: $viewModel.passphrase)
                        .textFieldStyle(.roundedBorder)
                }
            } else {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Text(String(localized: "Enter your 12-word recovery seed"))
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.onSurfaceVariant)
                    SeedPhraseGrid(
                        words: $viewModel.seedWords,
                        getSuggestions: { viewModel.getSuggestions($0) },
                        isValidWord: { viewModel.isValidWord($0) }
                    )
                }
            }
        }
    }

    // MARK: - Step 3: Preview

    private var previewStep: some View {
        VStack(spacing: Spacing.lg) {
            if let preview = viewModel.preview {
                // Backup info card
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    infoRow(label: String(localized: "Created"), value: viewModel.previewDateText)
                    infoRow(label: String(localized: "App Version"), value: preview.appVersion)
                    infoRow(label: String(localized: "Environment"), value: preview.environment)
                }
                .padding(Spacing.md)
                .background(colors.surface)
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))

                // Data counts card
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Text(String(localized: "Backup Contents"))
                        .font(AccBotFonts.body)
                        .bold()

                    countRow(icon: "doc.text", label: String(localized: "Plans"), count: preview.planCount)
                    if preview.hasSettings {
                        countRow(icon: "gearshape", label: String(localized: "Settings"), count: 1)
                    }
                    if preview.credentialCount > 0 {
                        countRow(icon: "key", label: String(localized: "Credentials"), count: preview.credentialCount)
                    }
                    if preview.transactionCount > 0 {
                        countRow(icon: "list.bullet.rectangle", label: String(localized: "Transactions"), count: preview.transactionCount)
                    }
                    if preview.notificationCount > 0 {
                        countRow(icon: "bell", label: String(localized: "Notifications"), count: preview.notificationCount)
                    }
                    if preview.withdrawalCount > 0 {
                        countRow(icon: "arrow.up.forward", label: String(localized: "Withdrawals"), count: preview.withdrawalCount)
                    }
                    if preview.thresholdCount > 0 {
                        countRow(icon: "gauge.medium", label: String(localized: "Thresholds"), count: preview.thresholdCount)
                    }
                }
                .padding(Spacing.md)
                .background(colors.surface)
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))

                // Restore mode
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Text(String(localized: "Restore Mode"))
                        .font(AccBotFonts.body)
                        .bold()

                    Picker(String(localized: "Restore mode"), selection: $viewModel.restoreMode) {
                        Text(String(localized: "Merge")).tag(RestoreMode.merge)
                        Text(String(localized: "Replace")).tag(RestoreMode.replace)
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()

                    // Warning card
                    HStack(alignment: .top) {
                        Image(systemName: viewModel.restoreMode == .replace ? "exclamationmark.triangle.fill" : "info.circle.fill")
                            .foregroundStyle(viewModel.restoreMode == .replace ? colors.error : colors.warning)
                            .accessibilityHidden(true)
                        Text(viewModel.restoreMode == .merge ?
                             String(localized: "Existing data will be kept. Duplicates will be skipped.") :
                             String(localized: "All existing data will be DELETED and replaced with backup data. This cannot be undone."))
                            .font(AccBotFonts.bodySmall)
                            .foregroundStyle(viewModel.restoreMode == .replace ? colors.error : colors.warning)
                    }
                    .padding(Spacing.md)
                    .background((viewModel.restoreMode == .replace ? colors.error : colors.warning).opacity(0.1))
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                }
                .padding(Spacing.md)
                .background(colors.surface)
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            }
        }
    }

    private func infoRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(AccBotFonts.bodySmall)
                .foregroundStyle(colors.onSurfaceVariant)
            Spacer()
            Text(value)
                .font(AccBotFonts.bodySmall)
        }
    }

    private func countRow(icon: String, label: String, count: Int) -> some View {
        HStack {
            Image(systemName: icon)
                .foregroundStyle(colors.primary)
                .frame(width: 20)
            Text(label)
                .font(AccBotFonts.bodySmall)
            Spacer()
            Text("\(count)")
                .font(AccBotFonts.bodySmall)
                .foregroundStyle(colors.onSurfaceVariant)
        }
    }

    // MARK: - Step 4: Result

    private var resultStep: some View {
        VStack(spacing: Spacing.lg) {
            Image(systemName: "checkmark.circle.fill")
                .font(AccBotFonts.iconLarge)
                .foregroundStyle(colors.primary)
                .accessibilityHidden(true)

            Text(String(localized: "Restore Complete"))
                .font(AccBotFonts.title)

            Text(String(localized: "Your data has been restored successfully. Please restart the app for all changes to take effect."))
                .font(AccBotFonts.body)
                .foregroundStyle(colors.onSurfaceVariant)
                .multilineTextAlignment(.center)

            Button {
                dismiss()
            } label: {
                Text(String(localized: "Done"))
                    .frame(maxWidth: .infinity)
                    .padding(Spacing.md)
                    .background(colors.primary)
                    .foregroundStyle(colors.onPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            }
        }
        .padding(.top, Spacing.xxxl)
    }

    // MARK: - Bottom Bar

    private var bottomBar: some View {
        HStack(spacing: Spacing.lg) {
            if viewModel.wizardStep == .enterPassword {
                Button(String(localized: "Back")) {
                    viewModel.wizardStep = .selectSource
                }
                .foregroundStyle(colors.primary)
            } else if viewModel.wizardStep == .preview {
                Button(String(localized: "Back")) {
                    viewModel.wizardStep = viewModel.isEncrypted ? .enterPassword : .selectSource
                }
                .foregroundStyle(colors.primary)
            }

            Spacer()

            Button {
                if viewModel.wizardStep == .enterPassword {
                    viewModel.attemptDecrypt()
                } else if viewModel.wizardStep == .preview {
                    if viewModel.restoreMode == .replace {
                        showReplaceConfirmation = true
                    } else {
                        viewModel.executeRestore()
                    }
                }
            } label: {
                if viewModel.isRestoring {
                    ProgressView()
                        .tint(colors.onPrimary)
                } else {
                    Text(viewModel.wizardStep == .preview ?
                         String(localized: "Restore") :
                         String(localized: "Decrypt"))
                }
            }
            .padding(.horizontal, Spacing.xxl)
            .padding(.vertical, Spacing.md)
            .background(canProceed ? colors.primary : colors.primary.opacity(Opacity.disabled))
            .foregroundStyle(colors.onPrimary)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            .disabled(!canProceed || viewModel.isRestoring)
            .sheet(isPresented: $showReplaceConfirmation) {
                DestructiveConfirmSheet(
                    title: String(localized: "Confirm Replace"),
                    message: String(localized: "All existing data will be permanently deleted and replaced with backup data."),
                    confirmWord: "REPLACE",
                    confirmButtonLabel: String(localized: "Delete & Restore"),
                    onConfirm: {
                        viewModel.executeRestore()
                    }
                )
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
    }

    private var canProceed: Bool {
        switch viewModel.wizardStep {
        case .enterPassword: return viewModel.canAttemptDecrypt
        case .preview: return viewModel.payload != nil
        default: return false
        }
    }
}
