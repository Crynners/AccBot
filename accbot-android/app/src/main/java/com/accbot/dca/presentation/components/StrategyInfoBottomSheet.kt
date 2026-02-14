package com.accbot.dca.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.accbot.dca.R
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.defaultAthTiers
import com.accbot.dca.domain.model.defaultFearGreedTiers
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategyInfoBottomSheet(
    strategy: DcaStrategy,
    onDismiss: () -> Unit,
    currentMultiplier: Float? = null
) {
    val accentCol = accentColor()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentCol.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getStrategyIcon(strategy),
                        contentDescription = null,
                        tint = accentCol,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(strategy.displayNameRes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(strategy.descriptionRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Strategy-specific content
            when (strategy) {
                is DcaStrategy.Classic -> ClassicStrategyContent()
                is DcaStrategy.AthBased -> AthBasedStrategyContent(currentMultiplier)
                is DcaStrategy.FearAndGreed -> FearAndGreedStrategyContent(currentMultiplier)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Close button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentCol)
            ) {
                Text(stringResource(R.string.strategy_got_it))
            }
        }
    }
}

@Composable
private fun ClassicStrategyContent() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.strategy_how_it_works),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.strategy_classic_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                StrategyHighlight(
                    icon = Icons.Default.Repeat,
                    label = stringResource(R.string.strategy_classic_consistent),
                    description = stringResource(R.string.strategy_classic_consistent_desc)
                )
                Spacer(modifier = Modifier.width(24.dp))
                StrategyHighlight(
                    icon = Icons.Default.Psychology,
                    label = stringResource(R.string.strategy_classic_simple),
                    description = stringResource(R.string.strategy_classic_simple_desc)
                )
            }
        }
    }
}

@Composable
private fun AthBasedStrategyContent(currentMultiplier: Float?) {
    val successCol = successColor()
    val accentCol = accentColor()
    Column {
        // Current multiplier if available
        if (currentMultiplier != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = successCol.copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = successCol
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.strategy_current_multiplier),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.strategy_multiplier_percent, (currentMultiplier * 100).toInt()),
                            fontWeight = FontWeight.SemiBold,
                            color = successCol
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = stringResource(R.string.strategy_how_it_works),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.strategy_ath_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tiers table
        Text(
            text = stringResource(R.string.strategy_purchase_tiers),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.strategy_distance_from_ath),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.strategy_multiplier),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.5f)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Rows
                TierRow(stringResource(R.string.strategy_ath_tier_0_10), stringResource(R.string.strategy_ath_mult_50))
                TierRow(stringResource(R.string.strategy_ath_tier_10_30), stringResource(R.string.strategy_ath_mult_100))
                TierRow(stringResource(R.string.strategy_ath_tier_30_50), stringResource(R.string.strategy_ath_mult_150))
                TierRow(stringResource(R.string.strategy_ath_tier_50_70), stringResource(R.string.strategy_ath_mult_200))
                TierRow(stringResource(R.string.strategy_ath_tier_70_plus), stringResource(R.string.strategy_ath_mult_300))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Example
        Card(
            colors = CardDefaults.cardColors(
                containerColor = accentCol.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = accentCol,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.strategy_ath_example),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun FearAndGreedStrategyContent(currentMultiplier: Float?) {
    val successCol = successColor()
    val accentCol = accentColor()
    Column {
        // Current multiplier if available
        if (currentMultiplier != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = successCol.copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = successCol
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.strategy_current_multiplier),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.strategy_multiplier_percent, (currentMultiplier * 100).toInt()),
                            fontWeight = FontWeight.SemiBold,
                            color = successCol
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = stringResource(R.string.strategy_how_it_works),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.strategy_fg_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tiers table
        Text(
            text = stringResource(R.string.strategy_sentiment_tiers),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.strategy_market_sentiment),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.strategy_multiplier),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.5f)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Rows
                TierRow(stringResource(R.string.strategy_fg_extreme_fear), stringResource(R.string.strategy_fg_mult_250))
                TierRow(stringResource(R.string.strategy_fg_fear), stringResource(R.string.strategy_fg_mult_150))
                TierRow(stringResource(R.string.strategy_fg_neutral), stringResource(R.string.strategy_fg_mult_100))
                TierRow(stringResource(R.string.strategy_fg_greed), stringResource(R.string.strategy_fg_mult_50))
                TierRow(stringResource(R.string.strategy_fg_extreme_greed), stringResource(R.string.strategy_fg_mult_25))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Example
        Card(
            colors = CardDefaults.cardColors(
                containerColor = accentCol.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = accentCol,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.strategy_fg_example),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun TierRow(condition: String, multiplier: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = condition,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = multiplier,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = accentColor(),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.5f)
        )
    }
}

@Composable
private fun StrategyHighlight(
    icon: ImageVector,
    label: String,
    description: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor(),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getStrategyIcon(strategy: DcaStrategy): ImageVector = when (strategy) {
    is DcaStrategy.Classic -> Icons.Default.Repeat
    is DcaStrategy.AthBased -> Icons.AutoMirrored.Filled.TrendingDown
    is DcaStrategy.FearAndGreed -> Icons.Default.Psychology
}

/**
 * Compact strategy chip for displaying on Dashboard
 */
@Composable
fun StrategyChip(
    strategy: DcaStrategy,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val accentCol = accentColor()
    val chipModifier = if (onClick != null) {
        modifier.then(Modifier)
    } else {
        modifier
    }

    AssistChip(
        onClick = { onClick?.invoke() },
        label = {
            Text(
                text = stringResource(strategy.displayNameRes).replace(" DCA", ""),
                style = MaterialTheme.typography.labelSmall
            )
        },
        leadingIcon = {
            Icon(
                imageVector = getStrategyIcon(strategy),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        modifier = chipModifier,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = accentCol.copy(alpha = 0.1f),
            labelColor = accentCol,
            leadingIconContentColor = accentCol
        ),
        border = null
    )
}
