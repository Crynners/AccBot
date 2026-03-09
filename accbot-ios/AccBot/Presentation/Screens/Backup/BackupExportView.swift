import SwiftUI

struct BackupExportView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @StateObject private var viewModel = BackupExportViewModel()
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accBotColors) private var colors
    @State private var showShareSheet = false
    @State private var qrImage: UIImage?

    var body: some View {
        VStack(spacing: 0) {
            BackupStepIndicator(
                steps: ExportWizardStep.allCases.map { $0.title },
                currentStep: viewModel.wizardStep.rawValue
            )

            ScrollView {
                VStack(spacing: Spacing.lg) {
                    switch viewModel.wizardStep {
                    case .selectData:
                        selectDataStep
                    case .encryption:
                        encryptionStep
                    case .result:
                        resultStep
                    }
                }
                .padding(Spacing.lg)
                .maxFormWidth()
            }

            if let error = viewModel.error {
                Text(error)
                    .font(AccBotFonts.bodySmall)
                    .foregroundStyle(colors.error)
                    .padding(.horizontal, Spacing.lg)
            }

            bottomBar
        }
        .scrollDismissesKeyboard(.interactively)
        .background(colors.background)
        .animation(.easeInOut(duration: 0.3), value: viewModel.wizardStep)
        .navigationTitle(String(localized: "Export Backup"))
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { viewModel.setup(dependencies) }
        .onChange(of: viewModel.wizardStep) { newStep in
            if newStep == .result, viewModel.isQrFeasible,
               let qrData = viewModel.generateQrCode() {
                qrImage = UIImage(data: qrData)
            }
        }
        .sheet(isPresented: $showShareSheet) {
            if let url = viewModel.getShareUrl() {
                ShareSheet(activityItems: [url])
            }
        }
    }

    // MARK: - Step 1: Select Data

    private var selectDataStep: some View {
        VStack(spacing: Spacing.md) {
            dataRow(
                icon: "doc.text",
                title: String(localized: "DCA Plans"),
                subtitle: String(localized: "\(viewModel.dataCounts.planCount) plans"),
                isIncluded: .constant(true),
                alwaysIncluded: true
            )
            dataRow(
                icon: "gearshape",
                title: String(localized: "Settings"),
                subtitle: String(localized: "App preferences"),
                isIncluded: .constant(true),
                alwaysIncluded: true
            )
            dataRow(
                icon: "key",
                title: String(localized: "Exchange Credentials"),
                subtitle: String(localized: "\(viewModel.dataCounts.credentialCount) exchanges"),
                isIncluded: $viewModel.includeCredentials
            )
            dataRow(
                icon: "list.bullet.rectangle",
                title: String(localized: "Transactions"),
                subtitle: String(localized: "\(viewModel.dataCounts.transactionCount) transactions"),
                isIncluded: $viewModel.includeTransactions
            )
            dataRow(
                icon: "bell",
                title: String(localized: "Notifications"),
                subtitle: String(localized: "\(viewModel.dataCounts.notificationCount) notifications"),
                isIncluded: $viewModel.includeNotifications
            )
            dataRow(
                icon: "arrow.up.forward",
                title: String(localized: "Withdrawals"),
                subtitle: String(localized: "\(viewModel.dataCounts.withdrawalCount) withdrawals"),
                isIncluded: $viewModel.includeWithdrawals
            )
        }
    }

    private func dataRow(icon: String, title: String, subtitle: String, isIncluded: Binding<Bool>, alwaysIncluded: Bool = false) -> some View {
        HStack {
            Image(systemName: icon)
                .foregroundStyle(colors.primary)
                .frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(AccBotFonts.body)
                Text(subtitle)
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
            }
            Spacer()
            if alwaysIncluded {
                Text(String(localized: "Included"))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.primary)
            } else {
                Toggle(title, isOn: isIncluded)
                    .tint(colors.primary)
                    .labelsHidden()
            }
        }
        .padding(Spacing.md)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    // MARK: - Step 2: Encryption

    private var encryptionStep: some View {
        VStack(spacing: Spacing.lg) {
            // Mode selector
            Picker(String(localized: "Encryption method"), selection: $viewModel.encryptionMode) {
                Text(String(localized: "Password")).tag(EncryptionMode.password)
                Text(String(localized: "12-Word Seed")).tag(EncryptionMode.seed)
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            .onChange(of: viewModel.encryptionMode) { newValue in
                if newValue == .seed && viewModel.seedWords.isEmpty {
                    viewModel.generateSeed()
                }
            }

            if viewModel.encryptionMode == .password {
                passwordInputSection
            } else {
                seedSection
            }
        }
    }

    private var passwordInputSection: some View {
        VStack(spacing: Spacing.md) {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(String(localized: "Password"))
                    .font(AccBotFonts.bodySmall)
                    .foregroundStyle(colors.onSurfaceVariant)
                SecureField(String(localized: "Minimum 8 characters"), text: $viewModel.password)
                    .textFieldStyle(.roundedBorder)

                // Password strength indicator
                if !viewModel.password.isEmpty {
                    passwordStrengthIndicator
                }
            }

            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(String(localized: "Confirm Password"))
                    .font(AccBotFonts.bodySmall)
                    .foregroundStyle(colors.onSurfaceVariant)
                SecureField(String(localized: "Repeat password"), text: $viewModel.confirmPassword)
                    .textFieldStyle(.roundedBorder)
            }

            if let error = viewModel.passwordError {
                Text(error)
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.error)
            }
        }
    }

    private var passwordStrengthIndicator: some View {
        let strength = passwordStrength(viewModel.password)
        return HStack(spacing: Spacing.xs) {
            ForEach(0..<4, id: \.self) { index in
                Capsule()
                    .fill(index < strength.level ? strength.color : colors.surfaceVariant)
                    .frame(height: 4)
            }
            Text(strength.label)
                .font(AccBotFonts.captionSmall)
                .foregroundStyle(strength.color)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(String(localized: "Password strength: \(strength.label)"))
    }

    private func passwordStrength(_ password: String) -> (level: Int, label: String, color: Color) {
        let length = password.count
        let hasUpper = password.rangeOfCharacter(from: .uppercaseLetters) != nil
        let hasLower = password.rangeOfCharacter(from: .lowercaseLetters) != nil
        let hasDigit = password.rangeOfCharacter(from: .decimalDigits) != nil
        let hasSpecial = password.rangeOfCharacter(from: CharacterSet.alphanumerics.inverted) != nil
        let varietyCount = [hasUpper, hasLower, hasDigit, hasSpecial].filter { $0 }.count

        if length < 8 {
            return (1, String(localized: "Too short"), colors.error)
        } else if length < 12 && varietyCount < 3 {
            return (2, String(localized: "Weak"), colors.warning)
        } else if length >= 12 && varietyCount >= 3 {
            return (4, String(localized: "Strong"), colors.success)
        } else {
            return (3, String(localized: "Good"), colors.primaryText)
        }
    }

    private var seedSection: some View {
        VStack(spacing: Spacing.md) {
            HStack {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(colors.warning)
                    .accessibilityHidden(true)
                Text(String(localized: "Write down these 12 words in order. You will need them to restore your backup."))
                    .font(AccBotFonts.bodySmall)
                    .foregroundStyle(colors.warning)
            }
            .padding(Spacing.md)
            .background(colors.warning.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))

            HStack(spacing: Spacing.sm) {
                Image(systemName: "camera.fill")
                    .foregroundStyle(colors.error)
                    .accessibilityHidden(true)
                Text(String(localized: "Do not take a screenshot. Write the words on paper and store them securely."))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.error)
            }
            .padding(Spacing.sm)
            .background(colors.error.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))

            SeedPhraseDisplay(words: viewModel.seedWords)

            HStack(spacing: Spacing.sm) {
                Image(systemName: "doc.on.clipboard")
                    .foregroundStyle(colors.error)
                    .accessibilityHidden(true)
                Text(String(localized: "Do not copy or screenshot. Write down on paper only."))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.error)
            }
            .padding(Spacing.sm)
            .background(colors.error.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))

            Button {
                viewModel.generateSeed()
                viewModel.seedConfirmed = false
            } label: {
                Label(String(localized: "Generate New Seed"), systemImage: "arrow.triangle.2.circlepath")
                    .font(AccBotFonts.bodySmall)
            }
            .foregroundStyle(colors.primary)

            Toggle(isOn: $viewModel.seedConfirmed) {
                Text(String(localized: "I have written down my recovery seed"))
                    .font(AccBotFonts.bodySmall)
            }
            .tint(colors.primary)
        }
    }

    // MARK: - Step 3: Result

    private var resultStep: some View {
        VStack(spacing: Spacing.lg) {
            Image(systemName: "checkmark.circle.fill")
                .font(AccBotFonts.iconLarge)
                .foregroundStyle(colors.primary)
                .accessibilityHidden(true)

            Text(String(localized: "Backup Created"))
                .font(AccBotFonts.title)

            if let fileName = viewModel.resultFileName {
                Text(fileName)
                    .font(AccBotFonts.bodySmall)
                    .foregroundStyle(colors.onSurfaceVariant)
            }

            Text(formattedSize)
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.onSurfaceVariant)

            Button {
                showShareSheet = true
            } label: {
                Label(String(localized: "Share Backup File"), systemImage: "square.and.arrow.up")
                    .frame(maxWidth: .infinity)
                    .padding(Spacing.md)
                    .background(colors.primary)
                    .foregroundStyle(colors.onPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            }

            if viewModel.isQrFeasible, let uiImage = qrImage {
                VStack(spacing: Spacing.sm) {
                    Text(String(localized: "Or scan QR code"))
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.onSurfaceVariant)
                    Image(uiImage: uiImage)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 200, height: 200)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                        .accessibilityLabel(String(localized: "Backup QR code"))
                }
            }
        }
    }

    private var formattedSize: String {
        let bytes = viewModel.resultSizeBytes
        if bytes < 1024 { return "\(bytes) B" }
        let kb = Double(bytes) / 1024.0
        if kb < 1024 { return String(format: "%.1f KB", kb) }
        return String(format: "%.1f MB", kb / 1024.0)
    }

    // MARK: - Bottom Bar

    private var bottomBar: some View {
        HStack(spacing: Spacing.lg) {
            if viewModel.wizardStep != .selectData && viewModel.wizardStep != .result {
                Button(String(localized: "Back")) {
                    viewModel.previousStep()
                }
                .foregroundStyle(colors.primary)
            }

            Spacer()

            if viewModel.wizardStep != .result {
                Button {
                    viewModel.nextStep()
                } label: {
                    if viewModel.isCreating {
                        ProgressView()
                            .tint(colors.onPrimary)
                    } else {
                        Text(viewModel.wizardStep == .encryption ?
                             String(localized: "Create Backup") :
                             String(localized: "Next"))
                    }
                }
                .padding(.horizontal, Spacing.xxl)
                .padding(.vertical, Spacing.md)
                .background(canProceedCurrent ? colors.primary : colors.primary.opacity(Opacity.disabled))
                .foregroundStyle(colors.onPrimary)
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                .disabled(!canProceedCurrent || viewModel.isCreating)
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
    }

    private var canProceedCurrent: Bool {
        switch viewModel.wizardStep {
        case .selectData: return viewModel.canProceedFromData
        case .encryption: return viewModel.canProceedFromEncryption
        case .result: return false
        }
    }
}