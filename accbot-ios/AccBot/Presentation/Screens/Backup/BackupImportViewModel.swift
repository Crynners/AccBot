import Foundation
import UniformTypeIdentifiers

enum ImportWizardStep: Int, CaseIterable {
    case selectSource = 0
    case enterPassword = 1
    case preview = 2
    case result = 3

    var title: String {
        switch self {
        case .selectSource: return String(localized: "Source")
        case .enterPassword: return String(localized: "Decrypt")
        case .preview: return String(localized: "Preview")
        case .result: return String(localized: "Result")
        }
    }
}

@MainActor
final class BackupImportViewModel: ObservableObject {
    // Source
    @Published var envelopeJson: String?
    @Published var isEncrypted = false

    // Password
    @Published var inputMode: EncryptionMode = .password
    @Published var passphrase = ""
    @Published var seedWords: [String] = Array(repeating: "", count: 12)

    // Preview
    @Published var preview: BackupPreview?
    @Published var payload: BackupPayload?
    @Published var restoreMode: RestoreMode = .merge

    // Wizard
    @Published var wizardStep: ImportWizardStep = .selectSource
    @Published var isRestoring = false
    @Published var isParsing = false
    @Published var restoreSuccess = false
    @Published var restoreWarning: String?
    @Published var error: String?

    private var restoreBackupUseCase: RestoreBackupUseCase?
    private var bip39: Bip39WordList?
    private var isSetUp = false

    private var deps: (useCase: RestoreBackupUseCase, bip39: Bip39WordList) {
        guard let useCase = restoreBackupUseCase, let bip39 = bip39 else {
            assertionFailure("BackupImportViewModel used before setup()")
            return (restoreBackupUseCase!, bip39!)
        }
        return (useCase, bip39)
    }

    func setup(_ dependencies: AppDependencies) {
        guard !isSetUp else { return }
        isSetUp = true
        let bip39 = Bip39WordList()
        let crypto = BackupCryptoUseCase(bip39: bip39)
        let restorer = BackupDataRestorer(
            database: dependencies.activeDatabase,
            credentialsStore: dependencies.credentialsStore,
            userPreferences: dependencies.userPreferences
        )
        self.restoreBackupUseCase = RestoreBackupUseCase(restorer: restorer, crypto: crypto, userPreferences: dependencies.userPreferences)
        self.bip39 = bip39
    }

    // MARK: - Source handling

    func handleFileSelected(url: URL) {
        do {
            guard url.startAccessingSecurityScopedResource() else {
                error = String(localized: "Cannot access selected file")
                return
            }
            defer { url.stopAccessingSecurityScopedResource() }

            let file = try FileHandle(forReadingFrom: url)
            defer { try? file.close() }
            let data = try file.read(upToCount: BackupEnvelope.maxEnvelopeBytes + 1) ?? Data()
            guard data.count <= BackupEnvelope.maxEnvelopeBytes else {
                error = String(localized: "This backup exceeds the supported size limit.")
                return
            }
            guard let json = String(data: data, encoding: .utf8) else {
                error = String(localized: "Invalid file format")
                return
            }
            processEnvelope(json: json)
        } catch {
            self.error = error.localizedDescription
        }
    }

    func handleQrScanned(content: String) {
        processEnvelope(json: content)
    }

    private func processEnvelope(json: String) {
        guard !isParsing else { return }
        guard json.utf8.count <= BackupEnvelope.maxEnvelopeBytes else {
            error = String(localized: "This backup exceeds the supported size limit.")
            return
        }
        error = nil
        envelopeJson = json

        // Quick parse to check if encrypted
        if let data = json.data(using: .utf8),
           let envelope = try? JSONDecoder().decode(BackupEnvelope.self, from: data) {
            isEncrypted = envelope.encrypted

            if !envelope.encrypted {
                // Skip password step, go directly to preview
                attemptDecrypt()
                return
            }
        }

        wizardStep = .enterPassword
    }

    // MARK: - Decryption

    var resolvedPassphrase: String {
        switch inputMode {
        case .password: return passphrase
        case .seed: return seedWords.joined(separator: " ")
        }
    }

    var canAttemptDecrypt: Bool {
        guard !isParsing else { return false }
        switch inputMode {
        case .password: return passphrase.count >= 8
        case .seed: return seedWords.allSatisfy { !$0.isEmpty }
        }
    }

    func attemptDecrypt() {
        guard let json = envelopeJson, !isParsing else { return }
        error = nil
        isParsing = true
        let passphrase = resolvedPassphrase
        let useCase = deps.useCase
        Task {
            let result = await Task.detached(priority: .userInitiated) {
                useCase.parseAndPreview(envelopeJson: json, passphrase: passphrase)
            }.value
            isParsing = false
            switch result {
            case .previewReady(let p, let pl):
                preview = p
                payload = pl
                wizardStep = .preview
            case .error(let msg):
                error = msg
            case .restoreComplete:
                break
            }
        }
    }

    // MARK: - Restore

    func executeRestore() {
        guard let payload = payload, let environment = preview?.environment, !isRestoring else { return }
        isRestoring = true
        error = nil

        Task {
            let result = deps.useCase.restore(payload: payload, environment: environment, restoreMode: restoreMode)
            switch result {
            case .restoreComplete(let message):
                restoreSuccess = true
                restoreWarning = message.isEmpty ? nil : message
                wizardStep = .result
            case .error(let msg):
                error = msg
            case .previewReady:
                break
            }
            isRestoring = false
        }
    }

    // MARK: - BIP39

    func getSuggestions(_ prefix: String) -> [String] {
        deps.bip39.getSuggestions(prefix: prefix)
    }

    func isValidWord(_ word: String) -> Bool {
        deps.bip39.isValidWord(word)
    }

    // MARK: - Preview helpers

    var previewDateText: String {
        guard let preview = preview else { return "" }
        let date = Date(timeIntervalSince1970: Double(preview.createdAt) / 1000.0)
        return AccBotFormatters.mediumDate.string(from: date)
    }
}
