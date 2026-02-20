package com.accbot.dca.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accbot.dca.R
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.presentation.ui.theme.Error
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
    modifier: Modifier = Modifier
) {
    val successCol = successColor()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(
            size = 40.dp,
            backgroundColor = successCol.copy(alpha = 0.1f),
            cornerRadius = 10.dp
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = successCol,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

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
                        onClick = onInfoClick,
                        modifier = Modifier.size(32.dp)
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
