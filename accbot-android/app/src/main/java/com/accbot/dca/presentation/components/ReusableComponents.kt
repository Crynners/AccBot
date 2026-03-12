package com.accbot.dca.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.accbot.dca.domain.model.ExchangeInstructions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accbot.dca.R
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.domain.model.isStable
import com.accbot.dca.presentation.ui.theme.Error
import com.accbot.dca.presentation.ui.theme.Warning
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor

/**
 * Stable selectable chip component - replaces experimental FilterChip.
 * Uses only stable Material3 APIs (Surface, Text, Row).
 *
 * @param text The label text
 * @param selected Whether the chip is selected
 * @param onClick Callback when chip is clicked
 * @param trailingIcon Optional trailing icon (e.g., close icon for dismissible chips)
 */
@Composable
fun SelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = successColor(),
    unselectedColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val backgroundColor = if (selected) selectedColor else unselectedColor
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) selectedColor else MaterialTheme.colorScheme.outline

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            leadingIcon?.invoke()
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge
            )
            trailingIcon?.invoke()
        }
    }
}

/**
 * Reusable icon badge component for displaying icons with colored backgrounds.
 * Used for exchange avatars, crypto icons, status icons, etc.
 */
@Composable
fun IconBadge(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    cornerRadius: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Exchange avatar component displaying the exchange logo.
 * Reusable across exchange selection, exchange cards, and instructions screens.
 */
@Composable
fun ExchangeAvatar(
    exchange: Exchange,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isConnected: Boolean = false
) {
    val successCol = successColor()
    val accentCol = accentColor()
    IconBadge(
        modifier = modifier,
        size = size,
        backgroundColor = if (isConnected) {
            successCol.copy(alpha = 0.15f)
        } else {
            accentCol.copy(alpha = 0.15f)
        }
    ) {
        Image(
            painter = painterResource(exchange.logoRes),
            contentDescription = exchange.displayName,
            modifier = Modifier.size(size * 0.6f),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * Onboarding header component with title, subtitle, and optional progress indicator.
 * Used across all onboarding screens for consistent styling.
 */
@Composable
fun OnboardingHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    progress: Float? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        progress?.let { progressValue ->
            LinearProgressIndicator(
                progress = progressValue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accentColor()
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Transaction status mapping to icon and color.
 * Centralized to avoid duplication across TransactionCard and TransactionDetailsScreen.
 */
data class TransactionStatusStyle(
    val icon: ImageVector,
    val color: Color,
    val label: String
)

/**
 * Warning/orange color for partial status (not in theme, defined here)
 */
val WarningOrange = Color(0xFFFFA500)

/**
 * Get the style (icon, color, label) for a transaction status.
 */
@Composable
fun getTransactionStatusStyle(status: TransactionStatus): TransactionStatusStyle {
    val successCol = successColor()
    val accentCol = accentColor()
    return when (status) {
        TransactionStatus.COMPLETED -> TransactionStatusStyle(
            icon = Icons.Default.CheckCircle,
            color = successCol,
            label = stringResource(R.string.transaction_status_completed)
        )
        TransactionStatus.FAILED -> TransactionStatusStyle(
            icon = Icons.Default.Error,
            color = Error,
            label = stringResource(R.string.transaction_status_failed)
        )
        TransactionStatus.PENDING -> TransactionStatusStyle(
            icon = Icons.Default.Schedule,
            color = accentCol,
            label = stringResource(R.string.transaction_status_pending)
        )
        TransactionStatus.PARTIAL -> TransactionStatusStyle(
            icon = Icons.Default.RemoveCircle,
            color = WarningOrange,
            label = stringResource(R.string.transaction_status_partial)
        )
    }
}

/**
 * Feature highlight card used in onboarding Welcome screen.
 */
@Composable
fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    iconTint: Color = successColor()
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                size = 48.dp,
                backgroundColor = iconTint.copy(alpha = 0.15f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Next step item used in onboarding completion screen.
 */
@Composable
fun NextStepItem(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    cornerRadius: Dp = 10.dp,
    spacing: Dp = 16.dp
) {
    val successCol = successColor()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(
            size = size,
            backgroundColor = successCol.copy(alpha = 0.1f),
            cornerRadius = cornerRadius
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = successCol,
                modifier = Modifier.size(iconSize)
            )
        }

        Spacer(modifier = Modifier.width(spacing))

        Column {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Strategy option card used in AddPlanScreen and EditPlanScreen.
 * Displays the DCA strategy name, description, info button, and radio selection.
 */
@Composable
fun StrategyOption(
    strategy: DcaStrategy,
    isSelected: Boolean,
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val accentCol = accentColor()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                accentCol.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(strategy.displayNameRes),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) accentCol else MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onInfoClick
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.add_plan_strategy_info),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(strategy.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = accentCol
                )
            )
        }
    }
}

/**
 * Unified exchange tile used across all exchange selection screens.
 *
 * @param exchange The exchange to display
 * @param onClick Callback when tile is clicked
 * @param isSelected Green bg + border (AddPlan, Onboarding selection)
 * @param isConnected Green avatar bg (Management connected section)
 * @param subtitle Override subtitle; default shows crypto count
 */
@Composable
fun ExchangeSelectionTile(
    exchange: Exchange,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isConnected: Boolean = false,
    subtitle: String? = null
) {
    val successCol = successColor()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                successCol.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(
                brush = SolidColor(successCol)
            )
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ExchangeAvatar(
                exchange = exchange,
                size = 48.dp,
                isConnected = isConnected || isSelected
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = exchange.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) successCol else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle
                    ?: stringResource(R.string.add_exchange_cryptos, exchange.supportedCryptos.size),
                style = MaterialTheme.typography.bodySmall,
                color = if (isConnected) successCol else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!isConnected && !exchange.isStable) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.experimental_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = Warning,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * "Request Exchange" card — OutlinedCard with Add icon, used in all exchange grids.
 */
@Composable
fun RequestExchangeTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.add_exchange_request),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Toggle card for showing/hiding experimental exchanges.
 */
@Composable
fun ExperimentalExchangesToggle(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle(!isEnabled)
            }
            .semantics(mergeDescendants = true) { role = Role.Switch },
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) Warning.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = null,
                tint = if (isEnabled) Warning else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_experimental_exchanges),
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEnabled) Warning else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isEnabled) {
                        stringResource(R.string.settings_experimental_exchanges_enabled)
                    } else {
                        stringResource(R.string.settings_experimental_exchanges_disabled)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isEnabled) Warning else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics {},
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Warning,
                    checkedTrackColor = Warning.copy(alpha = 0.5f)
                )
            )
        }
    }
}

/**
 * Disclaimer dialog shown when enabling the experimental exchanges toggle.
 */
@Composable
fun ExperimentalToggleDisclaimer(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.experimental_warning_title)) },
        text = { Text(stringResource(R.string.experimental_warning_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.experimental_warning_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_back))
            }
        }
    )
}

/**
 * Disclaimer dialog shown when selecting an experimental (non-stable) exchange.
 */
@Composable
fun ExperimentalExchangeDisclaimer(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.experimental_exchange_warning_title)) },
        text = { Text(stringResource(R.string.experimental_exchange_warning_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.experimental_warning_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_back))
            }
        }
    )
}

/**
 * 2-column exchange selection grid using chunked(2) + Row.
 * Safe inside scrollable parents (unlike LazyVerticalGrid).
 * Used by Onboarding, AddPlan, and AddExchange flat-selection screens.
 */
@Composable
fun ExchangeSelectionGrid(
    exchanges: List<Exchange>,
    onExchangeClick: (Exchange) -> Unit,
    modifier: Modifier = Modifier,
    selectedExchange: Exchange? = null,
    onRequestExchangeClick: (() -> Unit)? = null
) {
    // Build list of items: exchanges + optional request card sentinel
    val hasRequestCard = onRequestExchangeClick != null
    val totalItems = exchanges.size + if (hasRequestCard) 1 else 0

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // chunked(2) over indices
        (0 until totalItems).chunked(2).forEach { rowIndices ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowIndices.forEach { index ->
                    if (index < exchanges.size) {
                        val exchange = exchanges[index]
                        ExchangeSelectionTile(
                            exchange = exchange,
                            isSelected = selectedExchange == exchange,
                            onClick = { onExchangeClick(exchange) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        // Request exchange card
                        RequestExchangeTile(
                            onClick = onRequestExchangeClick!!,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // Spacer for odd-count row
                if (rowIndices.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Exchange setup instructions card with numbered steps and link to API page.
 * Used in both onboarding ExchangeSetupScreen and AddPlanScreen.
 */
@Composable
fun ExchangeInstructionsCard(
    exchange: Exchange,
    instructions: ExchangeInstructions,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolvedUrl = instructions.urlRes?.let { stringResource(it) } ?: instructions.url
    val accentCol = accentColor()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.add_exchange_api_setup),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall
            )

            instructions.steps.forEachIndexed { index, stepResId ->
                Row {
                    Text(
                        "${index + 1}.",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(stringResource(stepResId), style = MaterialTheme.typography.bodySmall)
                }
            }

            if (resolvedUrl.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resolvedUrl))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentCol)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.add_exchange_open_api_page, exchange.displayName))
                }
            }

            Row(
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = accentCol,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.add_exchange_security_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Strategy selection section with strategy option cards and info bottom sheet.
 * Used in both AddPlanScreen and onboarding FirstPlanScreen.
 */
@Composable
fun StrategySelectionSection(
    selectedStrategy: DcaStrategy,
    onStrategySelected: (DcaStrategy) -> Unit
) {
    val strategies = remember {
        listOf(
            DcaStrategy.Classic,
            DcaStrategy.AthBased(),
            DcaStrategy.FearAndGreed()
        )
    }
    var showStrategyInfo by remember { mutableStateOf<DcaStrategy?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        strategies.forEach { strategy ->
            val isSelected = when {
                selectedStrategy is DcaStrategy.Classic && strategy is DcaStrategy.Classic -> true
                selectedStrategy is DcaStrategy.AthBased && strategy is DcaStrategy.AthBased -> true
                selectedStrategy is DcaStrategy.FearAndGreed && strategy is DcaStrategy.FearAndGreed -> true
                else -> false
            }
            StrategyOption(
                strategy = strategy,
                isSelected = isSelected,
                onClick = { onStrategySelected(strategy) },
                onInfoClick = { showStrategyInfo = strategy }
            )
        }
    }

    showStrategyInfo?.let { strategy ->
        StrategyInfoBottomSheet(
            strategy = strategy,
            onDismiss = { showStrategyInfo = null }
        )
    }
}
