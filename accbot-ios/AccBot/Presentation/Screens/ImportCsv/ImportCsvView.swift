import SwiftUI
import UniformTypeIdentifiers

struct ImportCsvView: View {
    let planId: Int64
    @EnvironmentObject var dependencies: AppDependencies
    @StateObject private var viewModel: ImportCsvViewModel
    @Environment(\.dismiss) var dismiss
    @State private var showFilePicker = false

    init(planId: Int64) {
        self.planId = planId
        _viewModel = StateObject(wrappedValue: ImportCsvViewModel(
            planId: planId,
            dependencies: AppDependencies()
        ))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.lg) {
                if let plan = viewModel.plan {
                    // Plan info
                    HStack {
                        Text(plan.pair)
                            .font(AccBotFonts.titleSmall)
                            .foregroundColor(.white)
                        Text(plan.exchange.displayName)
                            .font(AccBotFonts.bodySmall)
                            .foregroundColor(.onSurfaceVariantColor)
                    }
                }

                // Import mode selector
                if viewModel.plan?.exchange.supportsApiImport == true {
                    Picker("Import Mode", selection: $viewModel.importMode) {
                        ForEach(ImportCsvViewModel.ImportMode.allCases, id: \.self) { mode in
                            Text(mode.rawValue).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                if viewModel.isComplete {
                    completionView
                } else if viewModel.isImporting {
                    progressView
                } else {
                    importActionView
                }

                if let error = viewModel.errorMessage {
                    Text(error)
                        .font(AccBotFonts.bodySmall)
                        .foregroundColor(.errorRed)
                        .padding(Spacing.md)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.errorRed.opacity(0.1))
                        .cornerRadius(CornerRadius.sm)
                }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.top, Spacing.lg)
        }
        .background(Color.backgroundDark)
        .navigationTitle("Import History")
        .navigationBarTitleDisplayMode(.inline)
        .fileImporter(
            isPresented: $showFilePicker,
            allowedContentTypes: [UTType.commaSeparatedText],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                if let url = urls.first {
                    _ = url.startAccessingSecurityScopedResource()
                    viewModel.importFromCsv(url: url)
                    url.stopAccessingSecurityScopedResource()
                }
            case .failure:
                break
            }
        }
    }

    private var importActionView: some View {
        VStack(spacing: Spacing.md) {
            if viewModel.importMode == .csv {
                VStack(spacing: Spacing.sm) {
                    Image(systemName: "doc.text")
                        .font(.system(size: 48))
                        .foregroundColor(.onSurfaceVariantColor)
                    Text("Select a CSV file to import transaction history")
                        .font(AccBotFonts.body)
                        .foregroundColor(.onSurfaceVariantColor)
                        .multilineTextAlignment(.center)
                }
                .padding(.vertical, Spacing.xxl)

                Button {
                    showFilePicker = true
                } label: {
                    Label("Choose CSV File", systemImage: "folder")
                        .font(AccBotFonts.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.md)
                        .background(Color.accentTeal)
                        .foregroundColor(.surfaceDark)
                        .cornerRadius(CornerRadius.sm)
                }
            } else {
                VStack(spacing: Spacing.sm) {
                    Image(systemName: "arrow.down.circle")
                        .font(.system(size: 48))
                        .foregroundColor(.onSurfaceVariantColor)
                    Text("Import trade history directly from exchange API")
                        .font(AccBotFonts.body)
                        .foregroundColor(.onSurfaceVariantColor)
                        .multilineTextAlignment(.center)
                }
                .padding(.vertical, Spacing.xxl)

                Button {
                    viewModel.importFromApi()
                } label: {
                    Label("Import from API", systemImage: "arrow.down.circle")
                        .font(AccBotFonts.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.md)
                        .background(Color.accentTeal)
                        .foregroundColor(.surfaceDark)
                        .cornerRadius(CornerRadius.sm)
                }
            }
        }
    }

    private var progressView: some View {
        VStack(spacing: Spacing.md) {
            ProgressView(value: viewModel.progress)
                .tint(.accentTeal)

            Text("Importing... \(viewModel.importedCount) transactions")
                .font(AccBotFonts.body)
                .foregroundColor(.onSurfaceVariantColor)
        }
        .padding(.vertical, Spacing.xxl)
    }

    private var completionView: some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 48))
                .foregroundColor(.accentTeal)

            Text("Import Complete")
                .font(AccBotFonts.titleSmall)
                .foregroundColor(.white)

            Text("\(viewModel.importedCount) transactions imported")
                .font(AccBotFonts.body)
                .foregroundColor(.onSurfaceVariantColor)

            Button {
                dismiss()
            } label: {
                Text("Done")
                    .font(AccBotFonts.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.md)
                    .background(Color.accentTeal)
                    .foregroundColor(.surfaceDark)
                    .cornerRadius(CornerRadius.sm)
            }
        }
        .padding(.vertical, Spacing.xxl)
    }
}
