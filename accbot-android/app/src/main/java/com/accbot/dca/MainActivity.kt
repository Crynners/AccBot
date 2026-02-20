package com.accbot.dca

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.accbot.dca.data.local.OnboardingPreferences
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.presentation.components.AccBotBottomNav
import com.accbot.dca.presentation.components.AccBotNavRail
import com.accbot.dca.presentation.components.bottomNavItems
import com.accbot.dca.presentation.navigation.Screen
import com.accbot.dca.presentation.screens.BiometricLockScreen
import com.accbot.dca.presentation.screens.AddPlanScreen
import com.accbot.dca.presentation.screens.DashboardScreen
import com.accbot.dca.presentation.screens.HistoryScreen
import com.accbot.dca.presentation.screens.SettingsScreen
import com.accbot.dca.presentation.screens.exchanges.AddExchangeScreen
import com.accbot.dca.presentation.screens.exchanges.ExchangeDetailScreen
import com.accbot.dca.presentation.screens.exchanges.ExchangeManagementScreen
import com.accbot.dca.presentation.screens.onboarding.*
import com.accbot.dca.presentation.screens.history.TransactionDetailsScreen
import com.accbot.dca.presentation.screens.plans.EditPlanScreen
import com.accbot.dca.presentation.screens.plans.PlanDetailsScreen
import com.accbot.dca.presentation.screens.ImportCsvScreen
import com.accbot.dca.presentation.screens.portfolio.PortfolioScreen
import com.accbot.dca.presentation.screens.splash.SplashScreen
import com.accbot.dca.presentation.ui.theme.AccBotTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var onboardingPreferences: OnboardingPreferences

    @Inject
    lateinit var userPreferences: UserPreferences

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Handle permission result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request necessary permissions
        checkPermissions()

        // Request battery optimization exemption for reliable background execution
        requestBatteryOptimizationExemption()

        setContent {
            val isSandboxMode = userPreferences.isSandboxMode()
            var isUnlocked by rememberSaveable { mutableStateOf(false) }
            val biometricEnabled = userPreferences.isBiometricLockEnabled()

            AccBotTheme(isSandboxMode = isSandboxMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (biometricEnabled && !isUnlocked) {
                        BiometricLockScreen(
                            activity = this@MainActivity,
                            onAuthenticated = { isUnlocked = true }
                        )
                    } else {
                        AccBotApp(
                            isOnboardingCompleted = onboardingPreferences.isOnboardingCompleted(),
                            onOnboardingComplete = {
                                onboardingPreferences.setOnboardingCompleted(true)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val packageName = packageName

        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            // Show dialog explaining why this is needed
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}

@Composable
fun AccBotApp(
    isOnboardingCompleted: Boolean,
    onOnboardingComplete: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Detect orientation
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Determine start destination
    val startDestination = if (isOnboardingCompleted) {
        "main"
    } else {
        Screen.Splash.route
    }

    // Pager state for main tab swiping
    val pagerState = rememberPagerState(pageCount = { bottomNavItems.size })
    val coroutineScope = rememberCoroutineScope()
    val onTabSelected: (Int) -> Unit = { index ->
        coroutineScope.launch { pagerState.animateScrollToPage(index) }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Splash screen
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToDashboard = {
                    navController.navigate("main") {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // Onboarding flow
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onContinue = {
                    navController.navigate(Screen.Security.route)
                }
            )
        }

        composable(Screen.Security.route) {
            SecurityScreen(
                onContinue = {
                    navController.navigate(Screen.ExchangeSetup.route)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ExchangeSetup.route) {
            ExchangeSetupScreen(
                onContinue = {
                    navController.navigate(Screen.FirstPlan.route)
                },
                onSkip = {
                    navController.navigate(Screen.OnboardingComplete.route)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.FirstPlan.route) {
            FirstPlanScreen(
                onContinue = {
                    navController.navigate(Screen.OnboardingComplete.route)
                },
                onSkip = {
                    navController.navigate(Screen.OnboardingComplete.route)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.OnboardingComplete.route) {
            CompletionScreen(
                onFinish = {
                    onOnboardingComplete()
                    navController.navigate("main") {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        // Main screens — HorizontalPager with bottom nav / nav rail
        composable("main") {
            var isChartTouching by remember { mutableStateOf(false) }

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                ) {
                    AccBotNavRail(
                        selectedIndex = pagerState.currentPage,
                        onItemSelected = onTabSelected
                    )
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = !isChartTouching,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) { page ->
                        MainTabPage(
                            page = page,
                            navController = navController,
                            onSwitchToTab = onTabSelected,
                            onChartTouching = { isChartTouching = it }
                        )
                    }
                }
            } else {
                Scaffold(
                    contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                    bottomBar = {
                        AccBotBottomNav(
                            selectedIndex = pagerState.currentPage,
                            onItemSelected = onTabSelected
                        )
                    }
                ) { padding ->
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = !isChartTouching,
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                    ) { page ->
                        MainTabPage(
                            page = page,
                            navController = navController,
                            onSwitchToTab = onTabSelected,
                            onChartTouching = { isChartTouching = it }
                        )
                    }
                }
            }
        }

        // Plan screens
        composable(Screen.AddPlan.route) {
            AddPlanScreen(
                onNavigateBack = { navController.popBackStack() },
                onPlanCreated = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.PlanDetails.route,
            arguments = listOf(
                navArgument(Screen.PLAN_ID_ARG) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val planId = backStackEntry.arguments?.getLong(Screen.PLAN_ID_ARG) ?: return@composable
            PlanDetailsScreen(
                planId = planId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = {
                    navController.navigate(Screen.EditPlan.createRoute(planId))
                },
                onNavigateToImport = {
                    navController.navigate(Screen.ImportCsv.createRoute(planId))
                },
                onNavigateToHistory = { crypto, fiat ->
                    navController.navigate(Screen.History.createRoute(crypto, fiat))
                }
            )
        }

        composable(
            route = Screen.EditPlan.route,
            arguments = listOf(
                navArgument(Screen.PLAN_ID_ARG) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val planId = backStackEntry.arguments?.getLong(Screen.PLAN_ID_ARG) ?: return@composable
            EditPlanScreen(
                planId = planId,
                onNavigateBack = { navController.popBackStack() },
                onPlanUpdated = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ImportCsv.route,
            arguments = listOf(
                navArgument(Screen.PLAN_ID_ARG) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val planId = backStackEntry.arguments?.getLong(Screen.PLAN_ID_ARG) ?: return@composable
            ImportCsvScreen(
                planId = planId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.createRoute()) {
                        popUpTo(Screen.PlanDetails.createRoute(planId)) { inclusive = false }
                    }
                }
            )
        }

        // Exchange screens
        composable(Screen.ExchangeManagement.route) {
            ExchangeManagementScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddExchange = { exchangeName ->
                    navController.navigate(Screen.AddExchange.createRoute(exchangeName))
                },
                onNavigateToExchangeDetail = { exchangeName ->
                    navController.navigate(Screen.ExchangeDetail.createRoute(exchangeName))
                }
            )
        }

        composable(
            route = Screen.ExchangeDetail.route,
            arguments = listOf(
                navArgument(Screen.EXCHANGE_ARG) { type = NavType.StringType }
            )
        ) {
            ExchangeDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AddExchange.route,
            arguments = listOf(
                navArgument(Screen.EXCHANGE_ARG) {
                    type = NavType.StringType; nullable = true; defaultValue = null
                }
            )
        ) {
            AddExchangeScreen(
                onNavigateBack = { navController.popBackStack() },
                onExchangeAdded = { navController.popBackStack() }
            )
        }

        // History
        composable(
            route = Screen.History.route,
            arguments = listOf(
                navArgument("crypto") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("fiat") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTransactionDetails = { transactionId ->
                    navController.navigate(Screen.TransactionDetails.createRoute(transactionId))
                }
            )
        }

        composable(
            route = Screen.TransactionDetails.route,
            arguments = listOf(
                navArgument(Screen.TRANSACTION_ID_ARG) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getLong(Screen.TRANSACTION_ID_ARG) ?: return@composable
            TransactionDetailsScreen(
                transactionId = transactionId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun MainTabPage(
    page: Int,
    navController: androidx.navigation.NavController,
    onSwitchToTab: (Int) -> Unit,
    onChartTouching: (Boolean) -> Unit = {}
) {
    when (page) {
        0 -> DashboardScreen(
            onNavigateToPlans = { navController.navigate(Screen.AddPlan.route) },
            onNavigateToHistory = { navController.navigate(Screen.History.createRoute()) },
            onNavigateToSettings = { onSwitchToTab(2) },
            onNavigateToPlanDetails = { planId ->
                navController.navigate(Screen.PlanDetails.createRoute(planId))
            },
            onNavigateToPortfolio = { _, _ -> onSwitchToTab(1) }
        )
        1 -> PortfolioScreen(
            onNavigateBack = { onSwitchToTab(0) },
            onNavigateToHistory = { crypto, fiat ->
                navController.navigate(Screen.History.createRoute(crypto, fiat))
            },
            onChartTouching = onChartTouching
        )
        2 -> SettingsScreen(
            onNavigateBack = { onSwitchToTab(0) },
            onNavigateToExchanges = {
                navController.navigate(Screen.ExchangeManagement.route)
            }
        )
    }
}
