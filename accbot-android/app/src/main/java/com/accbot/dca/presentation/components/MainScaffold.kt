package com.accbot.dca.presentation.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.accbot.dca.R
import com.accbot.dca.presentation.navigation.Screen
import com.accbot.dca.presentation.ui.theme.successColor

/**
 * Bottom navigation items configuration
 */
data class NavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    NavItem(
        route = Screen.Dashboard.route,
        labelRes = R.string.nav_dashboard,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    NavItem(
        route = Screen.Portfolio.route,
        labelRes = R.string.nav_portfolio,
        selectedIcon = Icons.Filled.PieChart,
        unselectedIcon = Icons.Outlined.PieChart
    ),
    NavItem(
        route = Screen.Settings.route,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)

// Pre-computed set of bottom nav routes for efficient lookup (avoids creating new list on each recomposition)
private val bottomNavRoutes = bottomNavItems.map { it.route }.toSet()

/**
 * Main scaffold with bottom navigation
 * Used for the main app screens after onboarding
 */
@Composable
fun MainScaffold(
    navController: NavController,
    content: @Composable (Modifier) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Only show bottom nav on main screens (startsWith handles query params)
    val showBottomNav = bottomNavRoutes.any { currentRoute?.startsWith(it) == true }

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                AccBotBottomNav(
                    navController = navController,
                    currentRoute = currentRoute
                )
            }
        }
    ) { paddingValues ->
        content(Modifier.padding(paddingValues))
    }
}

@Composable
fun AccBotBottomNav(
    navController: NavController,
    currentRoute: String?
) {
    val successCol = successColor()
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        bottomNavItems.forEach { item ->
            val isSelected = currentRoute?.startsWith(item.route) == true

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = stringResource(item.labelRes),
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = stringResource(item.labelRes),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = successCol,
                    selectedTextColor = successCol,
                    indicatorColor = successCol.copy(alpha = 0.15f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
fun AccBotNavRail(
    navController: NavController,
    currentRoute: String?
) {
    val successCol = successColor()
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        bottomNavItems.forEach { item ->
            val isSelected = currentRoute?.startsWith(item.route) == true

            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = stringResource(item.labelRes),
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = stringResource(item.labelRes),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = successCol,
                    selectedTextColor = successCol,
                    indicatorColor = successCol.copy(alpha = 0.15f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
