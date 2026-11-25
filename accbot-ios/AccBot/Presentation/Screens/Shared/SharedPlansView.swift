import SwiftUI
import UniformTypeIdentifiers

/// Import of plans other people shared with this user.
@MainActor
final class SharedPlansViewModel: ObservableObject {
    @Published private(set) var importing = false
    /// A live-update share was picked and needs its password.
    @Published var passwordRequired = false
    @Published private(set) var wrongPassword = false
    @Published var message: String?
    /// Share ID of a newly imported plan, so the screen can open it.
    @Published var opened: String?

    /// Content of an encrypted share waiting for its password. Kept in memory only.
    private var pendingData: Data?
    private var dependencies: AppDependencies?

    func setup(_ dependencies: AppDependencies) {
        self.dependencies = dependencies
        SharedPlanStore.shared.reload()
    }

    func importFile(_ url: URL) {
        guard !importing else { return }
        let accessing = url.startAccessingSecurityScopedResource()
        defer { if accessing { url.stopAccessingSecurityScopedResource() } }
        let size = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
        guard size <= SharedPlanCodec.maxFileBytes, let data = try? Data(contentsOf: url) else {
            message = size > SharedPlanCodec.maxFileBytes
                ? SharedPlanError.tooLarge.message
                : String(localized: "The file could not be read.")
            return
        }
        run(data, password: nil)
    }

    func submitPassword(_ password: String) {
        guard let pendingData else { return }
        run(pendingData, password: password)
    }

    func cancelPassword() {
        pendingData = nil
        passwordRequired = false
        wrongPassword = false
        importing = false
    }

    private func run(_ data: Data, password: String?) {
        guard let dependencies else { return }
        importing = true
        Task {
            do {
                let result = try await ImportSharedPlanUseCase(dependencies: dependencies)
                    .execute(data: data, password: password)
                finish()
                switch result {
                case .saved(let plan, let replaced):
                    message = replaced
                        ? String(localized: "Updated “\(plan.displayName)”")
                        : String(localized: "Added “\(plan.displayName)”")
                    opened = plan.shareId
                case .outdated(let stored):
                    message = String(localized: "A newer snapshot of “\(stored.displayName)” is already saved.")
                }
            } catch SharedPlanError.passwordRequired {
                pendingData = data
                wrongPassword = false
                passwordRequired = true
            } catch SharedPlanError.wrongPassword {
                pendingData = data
                wrongPassword = true
                passwordRequired = true
            } catch let error as SharedPlanError {
                finish()
                message = error.message
            } catch ShareKeyProblem.notReadOnly {
                finish()
                message = String(localized: "The shared key can trade or withdraw, so AccBot refused it. Ask the sender for a read-only key.")
            } catch let problem as ShareKeyProblem {
                finish()
                message = problem.message
            } catch {
                finish()
                message = String(localized: "The file could not be read.")
            }
        }
    }

    private func finish() {
        pendingData = nil
        passwordRequired = false
        wrongPassword = false
        importing = false
    }
}

/// List of read-only plans other people shared with this user, plus import.
struct SharedPlansView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @Environment(\.accBotColors) private var colors
    @ObservedObject private var store = SharedPlanStore.shared
    @StateObject private var viewModel = SharedPlansViewModel()
    @State private var showFilePicker = false
    @State private var password = ""

    var body: some View {
        Group {
            if store.plans.isEmpty {
                EmptyStateView(
                    systemImage: "eye",
                    title: String(localized: "No shared plans yet"),
                    subtitle: String(localized: "When someone shares a plan with you from AccBot, save the file and import it here."),
                    actionTitle: String(localized: "Import shared plan"),
                    action: { showFilePicker = true }
                )
            } else {
                List {
                    Section {
                        ForEach(store.plans) { plan in
                            Button {
                                router.navigate(to: .sharedPlanDetail(plan.shareId))
                            } label: {
                                SharedPlanRow(plan: plan)
                            }
                            .listRowBackground(colors.surface)
                        }
                    } footer: {
                        Text(String(localized: "Snapshots of plans other people shared with you. They are read-only, never run and are not part of your portfolio."))
                    }
                }
                .scrollContentBackground(.hidden)
            }
        }
        .background(colors.background)
        .navigationTitle(String(localized: "Shared with me"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                if viewModel.importing && !viewModel.passwordRequired {
                    ProgressView()
                } else {
                    Button {
                        showFilePicker = true
                    } label: {
                        Image(systemName: "square.and.arrow.down")
                    }
                    .accessibilityLabel(String(localized: "Import shared plan"))
                }
            }
        }
        .fileImporter(isPresented: $showFilePicker, allowedContentTypes: [.json, .data], allowsMultipleSelection: false) { result in
            if case .success(let urls) = result, let url = urls.first {
                viewModel.importFile(url)
            }
        }
        .alert(String(localized: "Encrypted shared plan"), isPresented: $viewModel.passwordRequired) {
            SecureField(String(localized: "File password"), text: $password)
            Button(String(localized: "Open")) {
                viewModel.submitPassword(password)
                password = ""
            }
            Button(String(localized: "Cancel"), role: .cancel) {
                password = ""
                viewModel.cancelPassword()
            }
        } message: {
            Text(viewModel.wrongPassword
                 ? String(localized: "Wrong password.")
                 : String(localized: "This plan includes live updates. Enter the password you got from the sender."))
        }
        .alert(viewModel.message ?? "", isPresented: messageBinding) {
            Button(String(localized: "OK"), role: .cancel) {}
        }
        .onChange(of: viewModel.opened) { shareId in
            guard let shareId else { return }
            viewModel.opened = nil
            router.navigate(to: .sharedPlanDetail(shareId))
        }
        .onAppear { viewModel.setup(dependencies) }
    }

    private var messageBinding: Binding<Bool> {
        Binding(
            get: { viewModel.message != nil && !viewModel.passwordRequired },
            set: { if !$0 { viewModel.message = nil } }
        )
    }
}

private struct SharedPlanRow: View {
    let plan: SharedPlan
    @Environment(\.accBotColors) private var colors

    var body: some View {
        HStack(spacing: Spacing.md) {
            CryptoIcon(symbol: plan.crypto, size: 40)
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(plan.displayName)
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.onSurface)
                    .lineLimit(1)
                Text("\(plan.pair) · \(plan.exchangeDisplayName)")
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
                Text(String(localized: "Snapshot from \(AccBotFormatters.mediumDate.string(from: plan.sharedAt))"))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
                if plan.liveUpdates {
                    Text(String(localized: "Live updates"))
                        .font(AccBotFonts.captionSmall)
                        .foregroundStyle(colors.primary)
                }
                if plan.isSandbox {
                    Text(String(localized: "Test environment data"))
                        .font(AccBotFonts.captionSmall)
                        .foregroundStyle(colors.warning)
                }
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(AccBotFonts.captionSmall)
                .foregroundStyle(colors.onSurfaceVariant)
        }
        .padding(.vertical, Spacing.xs)
    }
}
