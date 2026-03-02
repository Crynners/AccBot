import Foundation
import UIKit
import CoreImage.CIFilterBuiltins

enum ExportWizardStep: Int, CaseIterable {
    case selectData = 0
    case encryption = 1
    case result = 2

    var title: String {
        switch self {
        case .selectData: return String(localized: "Select Data")
        case .encryption: return String(localized: "Encryption")
        case .result: return String(localized: "Result")
        }
    }
}

@MainActor
final class BackupExportViewModel: ObservableObject {
    // Data selection
    @Published var dataCounts = BackupDataCounts()
    @Published var includeCredentials = false
    @Published var includeTransactions = false
    @Published var includeNotifications = false
    @Published var includeWithdrawals = false

    // Encryption
    @Published var encryptionMode: EncryptionMode = .password
    @Published var password = ""
    @Published var confirmPassword = ""
    @Published var seedWords: [String] = []
    @Published var seedConfirmed = false

    // Wizard
    @Published var wizardStep: ExportWizardStep = .selectData
    @Published var isCreating = false
    @Published var resultJson: String?
    @Published var resultFileName: String?
    @Published var resultSizeBytes = 0
    @Published var error: String?

    private var createBackupUseCase: CreateBackupUseCase?
    private var bip39: Bip39WordList?
    private var collector: BackupDataCollector?
    private var isSetUp = false

    private var deps: (useCase: CreateBackupUseCase, bip39: Bip39WordList, collector: BackupDataCollector) {
        guard let useCase = createBackupUseCase, let bip39 = bip39, let collector = collector else {
            assertionFailure("BackupExportViewModel used before setup()")
            return (createBackupUseCase!, bip39!, collector!)
        }
        return (useCase, bip39, collector)
    }
    private var lastTempUrl: URL?

    deinit {
        if let url = lastTempUrl {
            try? FileManager.default.removeItem(at: url)
        }
    }

    func setup(_ dependencies: AppDependencies) {
        guard !isSetUp else { return }
        isSetUp = true
        let bip39 = Bip39WordList()
        let crypto = BackupCryptoUseCase(bip39: bip39)
        let collector = BackupDataCollector(
            database: dependencies.activeDatabase,
            credentialsStore: dependencies.credentialsStore,
            userPreferences: dependencies.userPreferences
        )
        self.createBackupUseCase = CreateBackupUseCase(collector: collector, crypto: crypto)
        self.bip39 = bip39
        self.collector = collector
        loadDataCounts()
    }

    func loadDataCounts() {
        do {
            dataCounts = try deps.collector.getDataCounts()
        } catch {
            self.error = error.localizedDescription
        }
    }

    func generateSeed() {
        seedWords = deps.bip39.generateSeed()
    }

    // MARK: - Validation

    var canProceedFromData: Bool { true } // Plans + settings always included

    var canProceedFromEncryption: Bool {
        switch encryptionMode {
        case .password:
            return password.count >= 8 && password == confirmPassword
        case .seed:
            return seedWords.count == 12 && seedConfirmed
        }
    }

    var passwordError: String? {
        if encryptionMode == .password {
            if !password.isEmpty && password.count < 8 {
                return String(localized: "Password must be at least 8 characters")
            }
            if !confirmPassword.isEmpty && password != confirmPassword {
                return String(localized: "Passwords do not match")
            }
        }
        return nil
    }

    // MARK: - Navigation

    func nextStep() {
        guard let next = ExportWizardStep(rawValue: wizardStep.rawValue + 1) else { return }
        if next == .result {
            createBackup()
        } else {
            if next == .encryption && encryptionMode == .seed && seedWords.isEmpty {
                generateSeed()
            }
            wizardStep = next
        }
    }

    func previousStep() {
        guard let prev = ExportWizardStep(rawValue: wizardStep.rawValue - 1) else { return }
        wizardStep = prev
    }

    // MARK: - Create backup

    private func createBackup() {
        isCreating = true
        error = nil

        let options = BackupExportOptions(
            includeCredentials: includeCredentials,
            includeTransactions: includeTransactions,
            includeNotifications: includeNotifications,
            includeWithdrawals: includeWithdrawals,
            encryptionMode: encryptionMode,
            password: password,
            seed: encryptionMode == .seed ? seedWords.joined(separator: " ") : ""
        )

        Task {
            do {
                let result = try deps.useCase.execute(options: options)
                switch result {
                case .success(let json, let fileName, let size):
                    resultJson = json
                    resultFileName = fileName
                    resultSizeBytes = size
                    wizardStep = .result
                case .error(let message):
                    error = message
                }
            } catch {
                self.error = error.localizedDescription
            }
            isCreating = false
        }
    }

    // MARK: - QR Code

    var isQrFeasible: Bool {
        deps.useCase.isQrFeasible(payloadSizeBytes: resultSizeBytes)
    }

    func generateQrCode() -> Data? {
        guard let json = resultJson else { return nil }
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(json.utf8)
        filter.correctionLevel = "M"
        guard let ciImage = filter.outputImage else { return nil }
        let transform = CGAffineTransform(scaleX: 8, y: 8)
        let scaled = ciImage.transformed(by: transform)
        let context = CIContext()
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        let uiImage = UIImage(cgImage: cgImage)
        return uiImage.pngData()
    }

    // MARK: - File sharing

    func getShareUrl() -> URL? {
        guard let json = resultJson, let fileName = resultFileName else { return nil }
        // Clean up previous temp file
        if let prev = lastTempUrl { try? FileManager.default.removeItem(at: prev) }
        let tempUrl = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
        try? json.write(to: tempUrl, atomically: true, encoding: .utf8)
        lastTempUrl = tempUrl
        return tempUrl
    }
}
