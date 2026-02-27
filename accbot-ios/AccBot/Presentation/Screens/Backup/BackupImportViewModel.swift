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
    @Published var restoreSuccess = false
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
        self.restoreBackupUseCase = RestoreBackupUseCase(restorer: restorer, crypto: crypto)
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

            let data = try Data(contentsOf: url)
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
        switch inputMode {
        case .password: return passphrase.count >= 8
        case .seed: return seedWords.allSatisfy { !$0.isEmpty }
        }
    }

    func attemptDecrypt() {
        guard let json = envelopeJson else { return }
        error = nil

        let result = deps.useCase.parseAndPreview(envelopeJson: json, passphrase: resolvedPassphrase)
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

    // MARK: - Restore

    func executeRestore() {
        guard let payload = payload else { return }
        isRestoring = true
        error = nil

        Task {
            let result = deps.useCase.restore(payload: payload, restoreMode: restoreMode)
            switch result {
            case .restoreComplete:
                restoreSuccess = true
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
