import SwiftUI

/// Which key a live-update share carries; see `LiveShareKey`.
enum ShareKeySource: Hashable {
    /// A new key the user creates on the exchange with read-only access (the default).
    case newReadOnly
    /// The key this plan trades with, after an explicit warning.
    case planKey
}

/// Options for sharing one of the user's own plans. Keys and passwords live in memory only.
@MainActor
final class SharePlanViewModel: ObservableObject {
    static let minPasswordLength = 8

    @Published var includeAmounts = true
    @Published var liveUpdates = false
    /// Switching the source always asks for the warning to be accepted again.
    @Published var keySource: ShareKeySource = .newReadOnly {
        didSet {
            if keySource != oldValue {
                planKeyAcknowledged = false
                keyProblem = nil
            }
        }
    }
    /// The user accepted that the recipient gets the plan's key with all its permissions.
    @Published var planKeyAcknowledged = false
    @Published var apiKey = "" { didSet { if apiKey != oldValue { resetCheck() } } }
    @Published var apiSecret = "" { didSet { if apiSecret != oldValue { resetCheck() } } }
    @Published var passphrase = "" { didSet { if passphrase != oldValue { resetCheck() } } }
    @Published var clientId = "" { didSet { if clientId != oldValue { resetCheck() } } }
    @Published var password = ""
    @Published var passwordConfirm = ""
    @Published var unverifiableConfirmed = false
    @Published private(set) var verifying = false
    @Published private(set) var keyCheck: ReadOnlyExchangeClient.KeyCheck?
    @Published private(set) var keyProblem: ShareKeyProblem?
    @Published private(set) var creating = false
    @Published var sharedFile: URL?
    @Published var error: String?

    let planId: Int64
    let exchange: Exchange
    private var dependencies: AppDependencies?

    init(planId: Int64, exchange: Exchange) {
        self.planId = planId
        self.exchange = exchange
    }

    func setup(_ dependencies: AppDependencies) {
        self.dependencies = dependencies
    }

    var credentials: ExchangeCredentials {
        ExchangeCredentials(
            exchange: exchange,
            apiKey: apiKey.trimmingCharacters(in: .whitespacesAndNewlines),
            apiSecret: apiSecret.trimmingCharacters(in: .whitespacesAndNewlines),
            passphrase: exchange.requiresPassphrase ? passphrase.trimmingCharacters(in: .whitespacesAndNewlines) : nil,
            clientId: exchange.requiresClientId ? clientId.trimmingCharacters(in: .whitespacesAndNewlines) : nil
        )
    }

    var keyComplete: Bool {
        let c = credentials
        return !c.apiKey.isEmpty && !c.apiSecret.isEmpty
            && (!exchange.requiresPassphrase || !(c.passphrase ?? "").isEmpty)
            && (!exchange.requiresClientId || !(c.clientId ?? "").isEmpty)
    }

    var passwordValid: Bool {
        password.count >= Self.minPasswordLength && password == passwordConfirm
    }

    var keyAccepted: Bool {
        switch keySource {
        case .newReadOnly:
            return keyCheck == .readOnly || (keyCheck == .unverifiable && unverifiableConfirmed)
        case .planKey:
            return planKeyAcknowledged
        }
    }

    var canShare: Bool {
        guard !creating, !verifying else { return false }
        return !liveUpdates || (keyAccepted && passwordValid)
    }

    func verifyKey() {
        guard let dependencies, keyComplete, !verifying else { return }
        verifying = true
        keyProblem = nil
        keyCheck = nil
        let credentials = credentials
        Task {
            do {
                keyCheck = try await VerifyShareKeyUseCase(dependencies: dependencies)
                    .execute(planId: planId, credentials: credentials)
            } catch let problem as ShareKeyProblem {
                keyProblem = problem
            } catch {
                keyProblem = .checkFailed
            }
            verifying = false
        }
    }

    func createShare() {
        guard let dependencies, canShare else { return }
        var live: LiveShareAccess?
        if liveUpdates {
            switch keySource {
            case .planKey:
                live = LiveShareAccess(key: .planKey, password: password)
            case .newReadOnly:
                guard let keyCheck else { return }
                live = LiveShareAccess(key: .separate(credentials, keyCheck: keyCheck), password: password)
            }
        }
        creating = true
        error = nil
        keyProblem = nil
        Task {
            do {
                let file = try await CreatePlanShareUseCase(dependencies: dependencies)
                    .execute(planId: planId, includeAmounts: includeAmounts, live: live)
                let url = FileManager.default.temporaryDirectory.appendingPathComponent(file.fileName)
                try file.content.write(to: url, options: [.atomic, .completeFileProtection])
                sharedFile = url
            } catch let problem as ShareKeyProblem {
                // Shown inline next to the key choice instead of a generic error.
                keyProblem = problem
            } catch {
                self.error = String(localized: "Could not create the share file.")
            }
            creating = false
        }
    }

    /// Removes the exported file once the share sheet is closed.
    func cleanUp() {
        if let sharedFile {
            try? FileManager.default.removeItem(at: sharedFile)
        }
        sharedFile = nil
    }

    private func resetCheck() {
        keyCheck = nil
        keyProblem = nil
        unverifiableConfirmed = false
    }
}

/// Sheet with the share options, opened from the plan detail.
struct SharePlanSheet: View {
    @EnvironmentObject var dependencies: AppDependencies
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accBotColors) private var colors
    @StateObject private var viewModel: SharePlanViewModel
    @State private var showShareSheet = false

    init(planId: Int64, exchange: Exchange) {
        _viewModel = StateObject(wrappedValue: SharePlanViewModel(planId: planId, exchange: exchange))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(String(localized: "Creates a snapshot file of this plan. The recipient opens it in AccBot and can only view it. To share the current state later, send the file again and it replaces the previous version."))
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.onSurfaceVariant)

                    Toggle(String(localized: "Include amounts"), isOn: amountsBinding)
                        .disabled(viewModel.liveUpdates)
                    Text(viewModel.includeAmounts || viewModel.liveUpdates
                         ? String(localized: "Purchase amounts, invested total and holdings are included.")
                         : String(localized: "Only dates, prices, average buy price and performance in percent are shared."))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)

                    if viewModel.exchange.supportsApiImport {
                        Toggle(String(localized: "Live updates"), isOn: $viewModel.liveUpdates)
                        Text(String(localized: "The recipient can fetch new trades from the exchange with an API key you add to the file."))
                            .font(AccBotFonts.caption)
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                }
                .listRowBackground(colors.surface)

                if viewModel.liveUpdates {
                    keySection
                    keyDetailSection
                    passwordSection
                }

                Section {
                    Text(neverSharedText)
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                    if let error = viewModel.error {
                        Text(error)
                            .font(AccBotFonts.caption)
                            .foregroundStyle(colors.error)
                    }
                }
                .listRowBackground(colors.surface)
            }
            .scrollContentBackground(.hidden)
            .background(colors.background)
            .navigationTitle(String(localized: "Share plan (read-only)"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(String(localized: "Cancel")) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if viewModel.creating {
                        ProgressView()
                    } else {
                        Button(String(localized: "Share")) { viewModel.createShare() }
                            .disabled(!viewModel.canShare)
                    }
                }
            }
            .sheet(isPresented: $showShareSheet, onDismiss: {
                viewModel.cleanUp()
                dismiss()
            }) {
                if let url = viewModel.sharedFile {
                    ShareSheet(activityItems: [url])
                }
            }
        }
        .onAppear { viewModel.setup(dependencies) }
        .onChange(of: viewModel.sharedFile) { url in
            if url != nil { showShareSheet = true }
        }
    }

    private var neverSharedText: String {
        if !viewModel.liveUpdates {
            return String(localized: "Never shared: API keys, order IDs, connection names and withdrawal addresses. The file cannot be used to run the plan.")
        }
        if viewModel.keySource == .planKey {
            return String(localized: "Never shared: order IDs, connection names and withdrawal addresses. The file is encrypted with your password.")
        }
        return String(localized: "Never shared: the key this plan trades with, order IDs, connection names and withdrawal addresses. The file is encrypted with your password.")
    }

    private var keySection: some View {
        Section {
            Picker(String(localized: "Which API key to share"), selection: $viewModel.keySource) {
                keySourceLabel(
                    title: String(localized: "New read-only key (recommended)"),
                    subtitle: String(localized: "Create a separate key on the exchange that cannot trade or withdraw.")
                )
                .tag(ShareKeySource.newReadOnly)
                keySourceLabel(
                    title: String(localized: "This plan’s API key"),
                    subtitle: String(localized: "No new key needed, but the recipient gets full access to the key.")
                )
                .tag(ShareKeySource.planKey)
            }
            .pickerStyle(.inline)
            .labelsHidden()
            .disabled(viewModel.verifying || viewModel.creating)
        } header: {
            Text(String(localized: "Which API key to share"))
        }
        .listRowBackground(colors.surface)
    }

    private func keySourceLabel(title: String, subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(AccBotFonts.body)
            Text(subtitle)
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.onSurfaceVariant)
        }
    }

    @ViewBuilder
    private var keyDetailSection: some View {
        switch viewModel.keySource {
        case .newReadOnly:
            newKeySection
        case .planKey:
            planKeySection
        }
    }

    /// The same credentials form (QR scan) as when connecting the exchange.
    private var newKeySection: some View {
        Section {
            Text(String(localized: "Create a new API key on \(viewModel.exchange.displayName) with read-only access: no trading and no withdrawals. Do not use the key this plan buys with. Anyone with the file and password can use this key outside AccBot, so only its exchange permissions protect you."))
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.onSurfaceVariant)

            ExchangeCredentialsForm(
                exchange: viewModel.exchange,
                apiKey: $viewModel.apiKey,
                apiSecret: $viewModel.apiSecret,
                passphrase: $viewModel.passphrase,
                clientId: $viewModel.clientId
            )
            .disabled(viewModel.verifying || viewModel.creating)
            .listRowInsets(EdgeInsets(top: Spacing.sm, leading: Spacing.sm, bottom: Spacing.sm, trailing: Spacing.sm))

            Button {
                viewModel.verifyKey()
            } label: {
                HStack {
                    Text(String(localized: "Check key"))
                    if viewModel.verifying {
                        Spacer()
                        ProgressView()
                    }
                }
            }
            .disabled(!viewModel.keyComplete || viewModel.verifying || viewModel.creating)

            switch viewModel.keyCheck {
            case .readOnly:
                Label(String(localized: "The exchange confirmed this key cannot trade or withdraw."), systemImage: "checkmark.shield")
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.success)
            case .unverifiable:
                Toggle(isOn: $viewModel.unverifiableConfirmed) {
                    Text(String(localized: "\(viewModel.exchange.displayName) cannot report key permissions. I confirm this key is read-only, without trading or withdrawals."))
                        .font(AccBotFonts.caption)
                }
            case .notReadOnly, .none:
                EmptyView()
            }
            keyProblemText
        } header: {
            Text(String(localized: "Read-only API key"))
        }
        .listRowBackground(colors.surface)
    }

    private var planKeySection: some View {
        Section {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Label(String(localized: "The recipient gets your trading key"), systemImage: "exclamationmark.triangle.fill")
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.error)
                Text(String(localized: "The file will contain the API key this plan buys with, with all its permissions on \(viewModel.exchange.displayName): trading and, if the key allows it, withdrawals. AccBot on the recipient’s side only reads with it, but anyone with the file and password can use the key outside AccBot. To stop sharing, you have to delete or replace the key on the exchange, which also stops this plan until you enter a new key."))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurface)
            }
            .padding(.vertical, Spacing.xs)
            .listRowBackground(colors.error.opacity(0.12))

            Toggle(isOn: $viewModel.planKeyAcknowledged) {
                Text(String(localized: "I understand and want to share this plan’s key"))
                    .font(AccBotFonts.caption)
            }
            .disabled(viewModel.creating)
            .listRowBackground(colors.surface)

            keyProblemText
                .listRowBackground(colors.surface)
        }
    }

    @ViewBuilder
    private var keyProblemText: some View {
        if let problem = viewModel.keyProblem {
            Text(problem.message)
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.error)
        }
    }

    private var passwordSection: some View {
        Section {
            Text(String(localized: "The file will be encrypted. Send the password to the recipient another way than the file."))
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.onSurfaceVariant)
            SecureField(String(localized: "File password"), text: $viewModel.password)
            SecureField(String(localized: "Confirm password"), text: $viewModel.passwordConfirm)
            if !viewModel.password.isEmpty && !viewModel.passwordValid {
                Text(String(localized: "Use at least \(SharePlanViewModel.minPasswordLength) characters and type the same password twice."))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.error)
            }
        }
        .listRowBackground(colors.surface)
    }

    private var amountsBinding: Binding<Bool> {
        Binding(
            get: { viewModel.includeAmounts || viewModel.liveUpdates },
            set: { viewModel.includeAmounts = $0 }
        )
    }
}
