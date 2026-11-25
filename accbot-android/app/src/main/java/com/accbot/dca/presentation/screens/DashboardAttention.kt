package com.accbot.dca.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.accbot.dca.R
import com.accbot.dca.presentation.ui.theme.Error
import com.accbot.dca.presentation.ui.theme.Warning
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.utils.AutomationHealthChecker
import com.accbot.dca.presentation.utils.AutomationIssue
import com.accbot.dca.presentation.utils.NumberFormatters
import com.accbot.dca.presentation.utils.TimeUtils
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Tasks disappear once resolved; info notices can be dismissed. */
internal enum class AttentionTone { ERROR, WARNING, INFO }

@Composable
private fun AttentionTone.color(): Color = when (this) {
    AttentionTone.ERROR -> Error
    AttentionTone.WARNING -> Warning
    AttentionTone.INFO -> accentColor()
}

/**
 * One item of the dashboard "Needs attention" section. All notices share this layout:
 * icon + title, an explanation, and the actions that resolve it.
 */
@Composable
internal fun AttentionCard(
    icon: ImageVector,
    tone: AttentionTone,
    title: String,
    message: String? = null,
    onDismiss: (() -> Unit)? = null,
    dismissLabel: String? = null,
    content: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    val color = tone.color()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(top = 2.dp).size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                    if (message != null) {
                        Spacer(Modifier.padding(top = 2.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    content?.invoke()
                }
                if (onDismiss != null && dismissLabel == null) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.attention_dismiss),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            if (actions != null || (onDismiss != null && dismissLabel != null)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp)
                ) {
                    if (onDismiss != null && dismissLabel != null) {
                        TextButton(onClick = onDismiss) { Text(dismissLabel) }
                    }
                    actions?.invoke()
                }
            }
        }
    }
}

@Composable
private fun AttentionButton(text: String, tone: AttentionTone, onClick: () -> Unit) {
    val color = tone.color()
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = color.copy(alpha = 0.2f),
            contentColor = color
        )
    ) { Text(text) }
}

/** Callbacks of the dashboard attention section. */
internal class AttentionActions(
    val onReviewTransaction: (Long) -> Unit,
    val onRunRetryPlans: () -> Unit,
    val onDismissRetry: () -> Unit,
    val onExecuteMissed: (planId: Long, count: Int) -> Unit,
    val onSkipMissed: (planId: Long) -> Unit,
    val onDismissAutomation: () -> Unit,
    val onRefreshAutomation: () -> Unit,
    val onDismissSandbox: () -> Unit,
    val onDismissFirstPlanGuide: () -> Unit
)

/** What the attention section shows; computed outside composition so callers can skip it. */
internal class AttentionModel(state: DashboardUiState) {
    val review = state.purchasesAwaitingReview
    val retry = state.networkRetryInfo.takeIf { it.plans.isNotEmpty() && !it.dismissed }?.plans.orEmpty()
    val missed = state.missedPurchases
    val automationIssues = state.automationHealth.issues.takeIf { issues ->
        state.activePlans.any { it.plan.isEnabled } && issues.isNotEmpty() &&
            !state.dismissedAutomationIssues.containsAll(issues.map { it.key })
    }.orEmpty()
    val showSandbox = state.isSandboxMode && !state.sandboxNoticeDismissed
    val guidePlan = state.activePlans
        .filter { it.plan.isEnabled }
        .minByOrNull { it.plan.nextExecutionAt?.toEpochMilli() ?: Long.MAX_VALUE }
        ?.takeIf {
            state.purchaseOverviewLoaded && !state.firstPlanGuideDismissed &&
                state.lastPurchaseByPlan.isEmpty() && review.isEmpty()
        }
    val hasTasks = review.isNotEmpty() || retry.isNotEmpty() || missed.isNotEmpty() || automationIssues.isNotEmpty()
    val isVisible = hasTasks || showSandbox || guidePlan != null
}

/**
 * Everything on the dashboard that needs the user, in one place and one style:
 * tasks first (uncertain purchases, connection failures, missed purchases,
 * restricted automation), then information (sandbox, first-plan guide).
 */
@Composable
internal fun DashboardAttentionSection(model: AttentionModel, actions: AttentionActions) {
    if (!model.isVisible) return
    val context = LocalContext.current
    val review = model.review
    val retry = model.retry
    val automationIssues = model.automationIssues
    val showSandbox = model.showSandbox
    val guidePlan = model.guidePlan
    val hasTasks = model.hasTasks

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasTasks) {
            Text(
                text = stringResource(R.string.attention_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() }
            )
        }

        if (review.isNotEmpty()) {
            val first = review.first()
            AttentionCard(
                icon = Icons.Default.HelpOutline,
                tone = AttentionTone.ERROR,
                title = pluralStringResource(R.plurals.attention_review_title, review.size, review.size),
                message = stringResource(
                    R.string.attention_review_message,
                    "${first.crypto}/${first.fiat}",
                    first.exchange.displayName
                ),
                actions = {
                    AttentionButton(stringResource(R.string.attention_review_action), AttentionTone.ERROR) {
                        actions.onReviewTransaction(first.id)
                    }
                }
            )
        }

        if (retry.isNotEmpty()) {
            val attempts = retry.sumOf { it.retryCount }
            val nextRetry = retry.mapNotNull { it.nextRetryAt }.minOrNull()?.let {
                it.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("H:mm"))
            }
            AttentionCard(
                icon = Icons.Default.WifiOff,
                tone = AttentionTone.ERROR,
                title = stringResource(R.string.attention_network_title),
                message = retry.map {
                    stringResource(R.string.notification_network_retry_banner, it.crypto, it.exchangeName)
                }.joinToString("\n") + "\n" + listOfNotNull(
                    stringResource(R.string.notification_network_retry_banner_attempts, attempts),
                    nextRetry?.let { stringResource(R.string.notification_network_retry_banner_next, it) }
                ).joinToString(" · "),
                onDismiss = actions.onDismissRetry,
                actions = {
                    AttentionButton(stringResource(R.string.attention_retry_now), AttentionTone.ERROR, actions.onRunRetryPlans)
                }
            )
        }

        for (missed in model.missed) {
            AttentionCard(
                icon = Icons.Default.EventBusy,
                tone = AttentionTone.WARNING,
                title = stringResource(R.string.attention_missed_title),
                message = pluralStringResource(
                    R.plurals.attention_missed_message,
                    missed.missedCount,
                    missed.missedCount,
                    missed.crypto,
                    missed.exchangeName
                ),
                onDismiss = { actions.onSkipMissed(missed.planId) },
                dismissLabel = stringResource(R.string.missed_purchases_skip),
                actions = {
                    AttentionButton(stringResource(R.string.missed_purchases_buy), AttentionTone.WARNING) {
                        actions.onExecuteMissed(missed.planId, missed.missedCount)
                    }
                }
            )
        }

        if (automationIssues.isNotEmpty()) {
            AttentionCard(
                icon = Icons.Default.BatteryAlert,
                tone = AttentionTone.WARNING,
                title = stringResource(R.string.automation_status_limited),
                message = stringResource(R.string.automation_status_limited_message),
                onDismiss = actions.onDismissAutomation,
                dismissLabel = stringResource(R.string.attention_dont_remind),
                content = {
                    AutomationIssueList(automationIssues) { issue ->
                        AutomationHealthChecker.openSettings(context, issue)
                        actions.onRefreshAutomation()
                    }
                }
            )
        }

        if (showSandbox) {
            AttentionCard(
                icon = Icons.Default.Science,
                tone = AttentionTone.WARNING,
                title = stringResource(R.string.attention_sandbox_title),
                message = stringResource(R.string.attention_sandbox_message),
                onDismiss = actions.onDismissSandbox
            )
        }

        if (guidePlan != null) {
            val plan = guidePlan.plan
            val balance = guidePlan.fiatBalance
            AttentionCard(
                icon = Icons.Default.RocketLaunch,
                tone = AttentionTone.INFO,
                title = stringResource(R.string.first_plan_guide_title),
                message = listOfNotNull(
                    stringResource(
                        R.string.first_plan_guide_first_purchase,
                        TimeUtils.formatTimeUntil(plan.nextExecutionAt, context),
                        "${plan.amount.toPlainString()} ${plan.fiat}",
                        plan.exchange.displayName
                    ),
                    stringResource(R.string.first_plan_guide_funds, plan.fiat, plan.exchange.displayName),
                    if (balance != null && balance < plan.amount) {
                        stringResource(R.string.first_plan_guide_low_balance, "${NumberFormatters.fiat(balance)} ${plan.fiat}")
                    } else null,
                    stringResource(R.string.first_plan_guide_background)
                ).joinToString("\n"),
                onDismiss = actions.onDismissFirstPlanGuide,
                dismissLabel = stringResource(R.string.first_plan_guide_got_it)
            )
        }
    }
}

/** One row per automation problem, each with a button to the system screen that fixes it. */
@Composable
internal fun AutomationIssueList(issues: Collection<AutomationIssue>, onFix: (AutomationIssue) -> Unit) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        for (issue in AutomationIssue.entries.filter { it in issues }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(issue.messageRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onFix(issue) }) { Text(stringResource(R.string.automation_fix)) }
            }
        }
    }
}

internal fun AutomationIssue.messageRes(): Int = when (this) {
    AutomationIssue.BATTERY_OPTIMIZED -> R.string.automation_issue_battery
    AutomationIssue.EXACT_ALARMS_DENIED -> R.string.automation_issue_exact_alarms
    AutomationIssue.NOTIFICATIONS_BLOCKED -> R.string.automation_issue_notifications
}
