package com.accbot.dca.presentation.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.accbot.dca.BuildConfig
import com.accbot.dca.R
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.presentation.ui.theme.Error
import com.accbot.dca.presentation.ui.theme.Warning
import com.accbot.dca.presentation.ui.theme.successColor
import com.accbot.dca.presentation.utils.showBiometricPrompt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToExchanges: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showLowBalanceDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var dangerZoneExpanded by rememberSaveable { mutableStateOf(false) }

    // Refresh battery status when returning from system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBatteryStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.settings_delete_dialog_title)) },
            text = {
                Text(stringResource(R.string.settings_delete_dialog_text))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllData()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Error)
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Low balance threshold dialog
    if (showLowBalanceDialog) {
        val currentThreshold = remember { mutableIntStateOf(uiState.lowBalanceThresholdDays) }
        AlertDialog(
            onDismissRequest = { showLowBalanceDialog = false },
            title = { Text(stringResource(R.string.settings_low_balance_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_low_balance_dialog_text))
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { if (currentThreshold.intValue > 1) currentThreshold.intValue-- }
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.settings_decrease))
                        }
                        Text(
                            text = stringResource(R.string.settings_low_balance_days, currentThreshold.intValue),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        IconButton(
                            onClick = { if (currentThreshold.intValue < 14) currentThreshold.intValue++ }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_increase))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setLowBalanceThresholdDays(currentThreshold.intValue)
                    showLowBalanceDialog = false
                }) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLowBalanceDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Language picker dialog
    if (showLanguageDialog) {
        val currentTag = uiState.languageTag
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    LanguageOption(
                        label = stringResource(R.string.settings_language_system_default),
                        isSelected = currentTag.isEmpty(),
                        onClick = {
                            viewModel.setLanguage("")
                            showLanguageDialog = false
                        }
                    )
                    LanguageOption(
                        label = stringResource(R.string.settings_language_english),
                        isSelected = currentTag == "en",
                        onClick = {
                            viewModel.setLanguage("en")
                            showLanguageDialog = false
                        }
                    )
                    LanguageOption(
                        label = stringResource(R.string.settings_language_czech),
                        isSelected = currentTag == "cs",
                        onClick = {
                            viewModel.setLanguage("cs")
                            showLanguageDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Sandbox mode restart dialog
    if (uiState.showRestartDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRestartDialog() },
            title = { Text(stringResource(R.string.settings_restart_required)) },
            text = {
                Text(
                    stringResource(if (uiState.pendingSandboxMode) R.string.settings_restart_sandbox_on else R.string.settings_restart_sandbox_off)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmSandboxModeChange() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Warning)
                ) {
                    Text(stringResource(R.string.settings_restart_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRestartDialog() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Exchange Accounts section
            item {
                Text(
                    text = stringResource(R.string.settings_exchange_accounts),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_manage_exchanges),
                    subtitle = stringResource(R.string.settings_exchanges_connected, uiState.configuredExchanges.size),
                    icon = Icons.Default.AccountBalance,
                    onClick = { onNavigateToExchanges?.invoke() }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Battery Optimization
            item {
                Text(
                    text = stringResource(R.string.settings_system),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_battery_optimization),
                    subtitle = if (uiState.isBatteryOptimized) {
                        stringResource(R.string.settings_battery_disabled)
                    } else {
                        stringResource(R.string.settings_battery_unrestricted)
                    },
                    icon = Icons.Default.BatteryChargingFull,
                    showWarning = uiState.isBatteryOptimized,
                    onClick = {
                        try {
                            if (uiState.isBatteryOptimized) {
                                // Not yet whitelisted -> show system exemption dialog
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } else {
                                // Already whitelisted -> open general battery settings so user can review
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        } catch (_: Exception) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            } catch (_: Exception) {
                                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_battery_open_failed)) }
                            }
                        }
                    }
                )
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_low_balance_warning),
                    subtitle = stringResource(R.string.settings_low_balance_subtitle, uiState.lowBalanceThresholdDays),
                    icon = Icons.Default.Warning,
                    onClick = { showLowBalanceDialog = true }
                )
            }

            item {
                val languageLabel = when (uiState.languageTag) {
                    "en" -> stringResource(R.string.settings_language_english)
                    "cs" -> stringResource(R.string.settings_language_czech)
                    else -> stringResource(R.string.settings_language_system_default)
                }
                SettingsCard(
                    title = stringResource(R.string.settings_language),
                    subtitle = languageLabel,
                    icon = Icons.Default.Language,
                    onClick = { showLanguageDialog = true }
                )
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_subtitle),
                    icon = Icons.Default.Notifications,
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Security
            item {
                Text(
                    text = stringResource(R.string.settings_security),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                val activity = context as? FragmentActivity
                val biometricTitle = stringResource(R.string.biometric_prompt_title)
                val biometricSubtitle = stringResource(R.string.biometric_prompt_subtitle)

                BiometricToggleCard(
                    isEnabled = uiState.isBiometricLockEnabled,
                    onToggle = { requestedState ->
                        val biometricManager = BiometricManager.from(context)
                        val canAuthenticate = biometricManager.canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )

                        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.biometric_not_available)
                                )
                            }
                            return@BiometricToggleCard
                        }

                        if (activity != null) {
                            showBiometricPrompt(
                                activity = activity,
                                title = biometricTitle,
                                subtitle = biometricSubtitle,
                                onSuccess = {
                                    viewModel.setBiometricLockEnabled(requestedState)
                                }
                            )
                        }
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // About
            item {
                Text(
                    text = stringResource(R.string.settings_about),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_documentation),
                    subtitle = stringResource(R.string.settings_documentation_subtitle),
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/crynners/AccBot"))
                        context.startActivity(intent)
                    }
                )
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_report_issue),
                    subtitle = stringResource(R.string.settings_report_issue_subtitle),
                    icon = Icons.Default.BugReport,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/crynners/AccBot/issues"))
                        context.startActivity(intent)
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Developer Options
            item {
                Text(
                    text = stringResource(R.string.settings_developer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                SandboxToggleCard(
                    isEnabled = uiState.isSandboxMode,
                    onToggle = { viewModel.requestSandboxModeChange() }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Danger Zone (collapsible)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dangerZoneExpanded = !dangerZoneExpanded }
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_danger_zone),
                        style = MaterialTheme.typography.labelMedium,
                        color = Error
                    )
                    Icon(
                        imageVector = if (dangerZoneExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (dangerZoneExpanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                        tint = Error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = dangerZoneExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeleteDialog = true },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = Error.copy(alpha = 0.1f)
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(Error)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_delete_all_data),
                                    fontWeight = FontWeight.SemiBold,
                                    color = Error
                                )
                                Text(
                                    text = stringResource(R.string.settings_delete_all_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Version info
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.settings_accbot_dca),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.settings_built, BuildConfig.BUILD_DATE),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_made_with_love),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
        } // Box
    }
}

@Composable
internal fun ExchangeSettingsCard(
    exchange: Exchange,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = successColor(),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = exchange.displayName,
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.common_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun SettingsCardBase(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor
                )
            }
            trailing()
        }
    }
}

@Composable
internal fun SettingsCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    showWarning: Boolean = false,
    onClick: () -> Unit
) {
    SettingsCardBase(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTint = if (showWarning) Error else MaterialTheme.colorScheme.onSurfaceVariant,
        subtitleColor = if (showWarning) Error else MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick
    )
}

@Composable
private fun LanguageOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = successColor()
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
internal fun BiometricToggleCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val accent = successColor()
    val haptic = LocalHapticFeedback.current
    SettingsCardBase(
        title = stringResource(R.string.biometric_lock_title),
        subtitle = stringResource(R.string.biometric_lock_subtitle),
        icon = Icons.Default.Fingerprint,
        iconTint = if (isEnabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        trailing = {
            Switch(
                checked = isEnabled,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle(!isEnabled)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = accent,
                    checkedTrackColor = accent.copy(alpha = 0.5f)
                )
            )
        }
    )
}

@Composable
internal fun SandboxToggleCard(
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    SettingsCardBase(
        title = stringResource(R.string.settings_sandbox_mode),
        subtitle = if (isEnabled) {
            stringResource(R.string.settings_sandbox_enabled)
        } else {
            stringResource(R.string.settings_sandbox_disabled)
        },
        icon = Icons.Default.Science,
        iconTint = if (isEnabled) Warning else MaterialTheme.colorScheme.onSurfaceVariant,
        titleColor = if (isEnabled) Warning else MaterialTheme.colorScheme.onSurface,
        subtitleColor = if (isEnabled) Warning else MaterialTheme.colorScheme.onSurfaceVariant,
        containerColor = if (isEnabled) Warning.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
        trailing = {
            Switch(
                checked = isEnabled,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Warning,
                    checkedTrackColor = Warning.copy(alpha = 0.5f)
                )
            )
        }
    )
}
