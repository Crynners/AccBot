package com.accbot.dca.presentation.navigation

/**
 * Sealed class defining all navigation routes in the app
 */
sealed class Screen(val route: String) {
    // Onboarding flow
    data object Splash : Screen("splash")
    data object Welcome : Screen("onboarding/welcome")
    data object Security : Screen("onboarding/security")
    data object ExchangeSetup : Screen("onboarding/exchange_setup")
    data object FirstPlan : Screen("onboarding/first_plan")
    data object OnboardingComplete : Screen("onboarding/complete")

    // Main screens (with bottom nav)
    data object Dashboard : Screen("main/dashboard")
    data object Portfolio : Screen("main/portfolio")
    data object Settings : Screen("main/settings")

    // Plan screens
    data object AddPlan : Screen("plans/add")
    data object PlanDetails : Screen("plans/details/{planId}") {
        fun createRoute(planId: Long) = "plans/details/$planId"
    }
    data object EditPlan : Screen("plans/edit/{planId}") {
        fun createRoute(planId: Long) = "plans/edit/$planId"
    }
    data object ImportCsv : Screen("plans/import_csv/{planId}") {
        fun createRoute(planId: Long) = "plans/import_csv/$planId"
    }

    // Exchange screens
    data object ExchangeManagement : Screen("exchanges/manage")
    data object ExchangeDetail : Screen("exchanges/detail/{exchange}") {
        fun createRoute(exchangeName: String) = "exchanges/detail/$exchangeName"
    }
    data object AddExchange : Screen("exchanges/add?exchange={exchange}") {
        fun createRoute(exchangeName: String? = null): String {
            return if (exchangeName != null) "exchanges/add?exchange=$exchangeName" else "exchanges/add"
        }
    }

    // History screens
    data object History : Screen("history?crypto={crypto}&fiat={fiat}") {
        fun createRoute(crypto: String? = null, fiat: String? = null): String {
            val params = buildList {
                if (crypto != null) add("crypto=$crypto")
                if (fiat != null) add("fiat=$fiat")
            }
            return if (params.isEmpty()) "history" else "history?${params.joinToString("&")}"
        }
    }
    data object TransactionDetails : Screen("history/transaction/{transactionId}") {
        fun createRoute(transactionId: Long) = "history/transaction/$transactionId"
    }

    companion object {
        const val PLAN_ID_ARG = "planId"
        const val TRANSACTION_ID_ARG = "transactionId"
        const val EXCHANGE_ARG = "exchange"
    }
}

/**
 * Bottom navigation items
 */
enum class BottomNavItem(
    val route: String,
    val label: String
) {
    DASHBOARD(Screen.Dashboard.route, "Dashboard"),
    PORTFOLIO(Screen.Portfolio.route, "Portfolio"),
    SETTINGS(Screen.Settings.route, "Settings")
}
