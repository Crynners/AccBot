import SwiftUI

struct HistoryView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @StateObject private var viewModel: HistoryViewModel

    init(filterCrypto: String? = nil, filterFiat: String? = nil) {
        _viewModel = StateObject(wrappedValue: HistoryViewModel(
            dependencies: AppDependencies(),
            filterCrypto: filterCrypto,
            filterFiat: filterFiat
        ))
    }

    var body: some View {
        VStack(spacing: 0) {
            if viewModel.isLoading && viewModel.transactions.isEmpty {
                LoadingStateView(message: "Loading transactions...")
            } else if viewModel.transactions.isEmpty {
                EmptyStateView(
                    icon: "clock.arrow.circlepath",
                    title: "No Transactions",
                    subtitle: "Your DCA purchase history will appear here"
                )
            } else {
                transactionList
            }
        }
        .background(Color.backgroundDark)
        .navigationTitle("History")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button {
                        viewModel.showFilterSheet = true
                    } label: {
                        Label("Filter", systemImage: "line.3.horizontal.decrease.circle")
                    }

                    if viewModel.hasActiveFilters {
                        Button {
                            viewModel.clearFilters()
                        } label: {
                            Label("Clear Filters", systemImage: "xmark.circle")
                        }
                    }

                    Button {
                        viewModel.exportCsv()
                    } label: {
                        Label("Export CSV", systemImage: "square.and.arrow.up")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
        .sheet(isPresented: $viewModel.showFilterSheet) {
            filterSheet
        }
        .sheet(isPresented: $viewModel.showExportSheet) {
            if let url = viewModel.csvFileUrl {
                ShareSheet(items: [url])
            }
        }
        .onAppear {
            viewModel.loadData()
        }
    }

    private var transactionList: some View {
        List {
            ForEach(viewModel.transactions) { tx in
                TransactionCard(transaction: tx)
                    .onTapGesture {
                        router.navigate(to: .transactionDetails(tx.id))
                    }
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) {
                            viewModel.deleteTransaction(tx)
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                    .listRowBackground(Color.surfaceDark)
                    .onAppear {
                        // Pagination: load more when reaching end
                        if tx.id == viewModel.transactions.last?.id {
                            viewModel.loadNextPage()
                        }
                    }
            }
        }
        .listStyle(.plain)
    }

    private var filterSheet: some View {
        NavigationStack {
            Form {
                Section("Cryptocurrency") {
                    Picker("Crypto", selection: $viewModel.filterCrypto) {
                        Text("All").tag(nil as String?)
                        ForEach(["BTC", "ETH", "SOL", "ADA", "DOT", "LTC"], id: \.self) { crypto in
                            Text(crypto).tag(crypto as String?)
                        }
                    }
                }

                Section("Exchange") {
                    Picker("Exchange", selection: $viewModel.filterExchange) {
                        Text("All").tag(nil as Exchange?)
                        ForEach(Exchange.allCases) { exchange in
                            Text(exchange.displayName).tag(exchange as Exchange?)
                        }
                    }
                }

                Section("Status") {
                    Picker("Status", selection: $viewModel.filterStatus) {
                        Text("All").tag(nil as TransactionStatus?)
                        Text("Completed").tag(TransactionStatus.completed as TransactionStatus?)
                        Text("Failed").tag(TransactionStatus.failed as TransactionStatus?)
                        Text("Pending").tag(TransactionStatus.pending as TransactionStatus?)
                    }
                }

                Section("Date Range") {
                    DatePicker("From", selection: Binding(
                        get: { viewModel.filterDateFrom ?? Date.distantPast },
                        set: { viewModel.filterDateFrom = $0 }
                    ), displayedComponents: .date)

                    DatePicker("To", selection: Binding(
                        get: { viewModel.filterDateTo ?? Date() },
                        set: { viewModel.filterDateTo = $0 }
                    ), displayedComponents: .date)
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.backgroundDark)
            .navigationTitle("Filters")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        viewModel.showFilterSheet = false
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Apply") {
                        viewModel.showFilterSheet = false
                        viewModel.loadData()
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

/// UIActivityViewController wrapper for sharing
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
