import SwiftUI

struct HistoryView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @StateObject private var viewModel: HistoryViewModel
    @Environment(\.accBotColors) private var colors

    init(filterCrypto: String? = nil, filterFiat: String? = nil) {
        _viewModel = StateObject(wrappedValue: HistoryViewModel(
            filterCrypto: filterCrypto,
            filterFiat: filterFiat
        ))
    }

    var body: some View {
        VStack(spacing: 0) {
            // Active filter chips
            if viewModel.hasActiveFilters {
                activeFilterChips
            }

            if viewModel.isLoading && viewModel.filteredTransactions.isEmpty {
                LoadingStateView(message: "Loading transactions...")
            } else if viewModel.filteredTransactions.isEmpty {
                VStack(spacing: Spacing.lg) {
                    EmptyStateView(
                        systemImage: viewModel.hasActiveFilters ? "doc.text.magnifyingglass" : "clock.arrow.circlepath",
                        title: viewModel.hasActiveFilters
                            ? String(localized: "No Matching Transactions")
                            : String(localized: "No Transactions"),
                        subtitle: viewModel.hasActiveFilters
                            ? String(localized: "Try adjusting your filters")
                            : String(localized: "Your DCA purchase history will appear here")
                    )

                    if viewModel.hasActiveFilters {
                        Button {
                            viewModel.clearFilters()
                        } label: {
                            Text(String(localized: "Clear Filters"))
                                .font(AccBotFonts.headline)
                                .foregroundStyle(colors.primary)
                        }
                    }
                }
            } else {
                transactionList
            }
        }
        .background(colors.background)
        .navigationTitle(String(localized: "History"))
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                HStack(spacing: Spacing.sm) {
                    // Sort button with menu
                    sortMenu

                    // Filter button with badge
                    Button {
                        viewModel.showFilterSheet = true
                    } label: {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: "line.3.horizontal.decrease.circle")
                            if viewModel.hasActiveFilters {
                                Circle()
                                    .fill(colors.error)
                                    .frame(width: 8, height: 8)
                                    .offset(x: 2, y: -2)
                            }
                        }
                    }
                    .accessibilityLabel(String(localized: "Filter transactions"))
                    .accessibilityHint(viewModel.hasActiveFilters
                        ? String(localized: "\(viewModel.activeFilters.count) filters active")
                        : "")

                    // Export button
                    Button {
                        viewModel.exportCsv()
                    } label: {
                        Image(systemName: "square.and.arrow.up")
                    }
                    .accessibilityLabel(String(localized: "Export transactions"))
}
            }
        }
        .searchable(text: $viewModel.searchText, prompt: String(localized: "Search transactions…"))
        .sheet(isPresented: $viewModel.showFilterSheet) {
            filterSheet
        }
        .sheet(isPresented: $viewModel.showExportSheet) {
            if let url = viewModel.csvFileUrl {
                ShareSheet(activityItems: [url])
            }
        }
        .overlay(alignment: .bottom) {
            if viewModel.showUndoSnackbar {
                UndoSnackbar(
                    message: String(localized: "Transaction deleted"),
                    onUndo: { viewModel.undoDelete() }
                )
                .padding(.horizontal, Spacing.lg)
                .padding(.bottom, Spacing.xxl)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .animation(.easeInOut(duration: 0.3), value: viewModel.showUndoSnackbar)
            }
        }
        .alert(String(localized: "Delete Transaction?"), isPresented: $viewModel.showDeleteConfirmation) {
            Button(String(localized: "Cancel"), role: .cancel) {}
            Button(String(localized: "Delete"), role: .destructive) {
                viewModel.executeDelete()
            }
        } message: {
            if let tx = viewModel.transactionToDelete {
                Text(String(localized: "Delete \(tx.crypto)/\(tx.fiat) transaction from \(tx.executedAt.formatted(date: .abbreviated, time: .shortened))?"))
            }
        }
        .alert(String(localized: "Error"), isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button(String(localized: "OK"), role: .cancel) {}
        } message: {
            if let msg = viewModel.errorMessage {
                Text(msg)
            }
        }
        .onAppear {
            viewModel.setup(dependencies)
        }
    }

    // MARK: - Active Filter Chips

    private var activeFilterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(viewModel.activeFilters) { filter in
                    HStack(spacing: 4) {
                        Text(filter.label)
                            .font(AccBotFonts.caption)
                        Button {
                            viewModel.clearFilter(filter)
                        } label: {
                            Image(systemName: "xmark")
                                .font(AccBotFonts.captionSmall)
                                .frame(minWidth: 44, minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .accessibilityLabel(String(localized: "Remove \(filter.label) filter"))
                    }
                    .padding(.horizontal, Spacing.md)
                    .background(colors.primary.opacity(0.2))
                    .foregroundStyle(colors.primary)
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.xl))
                }

                Button(String(localized: "Clear All")) {
                    viewModel.clearFilters()
                }
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.onSurfaceVariant)
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.sm)
        }
        .background(colors.surface)
    }

    // MARK: - Sort Menu

    private var sortMenu: some View {
        Menu {
            ForEach(HistoryViewModel.SortOption.allCases, id: \.self) { option in
                Button {
                    viewModel.setSortOption(option)
                } label: {
                    HStack {
                        Text(option.localizedName)
                        if viewModel.sortOption == option {
                            Image(systemName: "checkmark")
                        }
                    }
                }
            }
        } label: {
            Image(systemName: "arrow.up.arrow.down")
        }
        .accessibilityLabel(String(localized: "Sort: \(viewModel.sortOption.localizedName)"))
    }

    // MARK: - Transaction List

    private var transactionList: some View {
        List {
            ForEach(viewModel.filteredTransactions) { tx in
                Button {
                    router.navigate(to: .transactionDetails(tx.id))
                } label: {
                    TransactionCard(transaction: tx)
                }
                .buttonStyle(.plain)
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) {
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                            viewModel.deleteTransaction(tx)
                        } label: {
                            Label(String(localized: "Delete"), systemImage: "trash")
                        }
                    }
                    .listRowBackground(colors.surface)
                    .onAppear {
                        if tx.id == viewModel.filteredTransactions.last?.id {
                            viewModel.loadNextPage()
                        }
                    }
            }

            if viewModel.hasMore {
                HStack {
                    Spacer()
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: colors.primary))
                    Spacer()
                }
                .listRowBackground(colors.background)
            } else if !viewModel.filteredTransactions.isEmpty {
                Text(String(localized: "\(viewModel.filteredTransactions.count) transactions"))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .frame(maxWidth: .infinity)
                    .listRowBackground(colors.background)
            }
        }
        .listStyle(.plain)
    }

    // MARK: - Filter Sheet

    private var filterSheet: some View {
        NavigationStack {
            Form {
                Section(String(localized: "Cryptocurrency")) {
                    Picker(String(localized: "Crypto"), selection: $viewModel.filterCrypto) {
                        Text(String(localized: "All")).tag(nil as String?)
                        ForEach(viewModel.availableCryptos, id: \.self) { crypto in
                            Text(crypto).tag(crypto as String?)
                        }
                    }
                }

                Section(String(localized: "Exchange")) {
                    Picker(String(localized: "Exchange"), selection: $viewModel.filterExchange) {
                        Text(String(localized: "All")).tag(nil as Exchange?)
                        ForEach(Exchange.allCases) { exchange in
                            Text(exchange.displayName).tag(exchange as Exchange?)
                        }
                    }
                }

                Section(String(localized: "Status")) {
                    Picker(String(localized: "Status"), selection: $viewModel.filterStatus) {
                        Text(String(localized: "All")).tag(nil as TransactionStatus?)
                        Text(String(localized: "Completed")).tag(TransactionStatus.completed as TransactionStatus?)
                        Text(String(localized: "Failed")).tag(TransactionStatus.failed as TransactionStatus?)
                        Text(String(localized: "Pending")).tag(TransactionStatus.pending as TransactionStatus?)
                    }
                }

                Section(String(localized: "Date Range")) {
                    DatePicker(String(localized: "From"), selection: Binding(
                        get: { viewModel.filterDateFrom ?? Date.distantPast },
                        set: { viewModel.filterDateFrom = $0 }
                    ), in: ...( viewModel.filterDateTo ?? Date()), displayedComponents: .date)

                    DatePicker(String(localized: "To"), selection: Binding(
                        get: { viewModel.filterDateTo ?? Date() },
                        set: { viewModel.filterDateTo = $0 }
                    ), in: (viewModel.filterDateFrom ?? .distantPast)...Date(), displayedComponents: .date)
                }
            }
            .scrollContentBackground(.hidden)
            .background(colors.background)
            .navigationTitle(String(localized: "Filters"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(String(localized: "Cancel")) {
                        viewModel.showFilterSheet = false
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(String(localized: "Apply")) {
                        viewModel.showFilterSheet = false
                        viewModel.loadData()
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
