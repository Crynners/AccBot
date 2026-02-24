import Foundation
import Combine

@MainActor
class PlanDetailsViewModel: ObservableObject {
    @Published var plan: DcaPlan?
    @Published var recentTransactions: [Transaction] = []
    @Published var isLoading: Bool = true
    @Published var errorMessage: String?

    private let planId: Int64
    private let dependencies: AppDependencies
    private var cancellables = Set<AnyCancellable>()

    init(planId: Int64, dependencies: AppDependencies) {
        self.planId = planId
        self.dependencies = dependencies
    }

    // MARK: - Loading

    func loadData() {
        Task {
            isLoading = true
            errorMessage = nil

            do {
                plan = try dependencies.activeDatabase.planDao.getById(planId)
                recentTransactions = try dependencies.activeDatabase.transactionDao.getByPlanId(
                    planId,
                    limit: 10
                )
            } catch {
                errorMessage = error.localizedDescription
            }

            isLoading = false
        }
    }

    // MARK: - Actions

    func toggleEnabled() {
        guard let plan = plan else { return }
        let newEnabled = !plan.isEnabled

        do {
            try dependencies.activeDatabase.planDao.setEnabled(id: planId, enabled: newEnabled)
            // Reload to reflect the change
            self.plan = try dependencies.activeDatabase.planDao.getById(planId)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func deletePlan() -> Bool {
        do {
            // Delete associated transactions first
            try dependencies.activeDatabase.transactionDao.deleteByPlanId(planId)
            // Delete the plan
            try dependencies.activeDatabase.planDao.delete(id: planId)
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }
}
