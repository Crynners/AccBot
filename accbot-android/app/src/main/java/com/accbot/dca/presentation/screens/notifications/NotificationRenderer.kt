package com.accbot.dca.presentation.screens.notifications

import android.content.Context
import com.accbot.dca.R
import com.accbot.dca.data.local.NotificationEntity
import com.accbot.dca.data.local.NotificationTemplateArgs
import com.accbot.dca.presentation.utils.NumberFormatters
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Renders notification title/message from structured [NotificationTemplateArgs]
 * using the current locale. Falls back to the pre-rendered text stored in the entity
 * when templateArgs is null (legacy notifications).
 */
object NotificationRenderer {

    /**
     * @return Pair(title, message) rendered in the current locale.
     */
    fun render(context: Context, entity: NotificationEntity): Pair<String, String> {
        val args = entity.templateArgs?.let { NotificationTemplateArgs.fromJson(it) }
            ?: return entity.title to entity.message
        return render(context, args)
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("H:mm")

    fun render(context: Context, args: NotificationTemplateArgs): Pair<String, String> {
        return when (args) {
            is NotificationTemplateArgs.Purchase -> {
                val title = context.getString(R.string.notification_purchase_title)
                var message = context.getString(
                    R.string.notification_purchase_text,
                    NumberFormatters.crypto(BigDecimal(args.cryptoAmount)),
                    args.crypto,
                    NumberFormatters.fiat(BigDecimal(args.fiatAmount)),
                    args.fiat,
                    NumberFormatters.fiat(BigDecimal(args.price))
                )
                message += formatDelaySuffix(context, args.scheduledAtEpochMs, args.executedAtEpochMs)
                title to message
            }

            is NotificationTemplateArgs.PurchasePending -> {
                val title = context.getString(R.string.notification_purchase_title)
                var message = context.getString(
                    R.string.notification_purchase_pending_text,
                    NumberFormatters.fiat(BigDecimal(args.fiatAmount)),
                    args.fiat,
                    args.crypto,
                    NumberFormatters.fiat(BigDecimal(args.price))
                )
                message += formatDelaySuffix(context, args.scheduledAtEpochMs, args.executedAtEpochMs)
                title to message
            }

            is NotificationTemplateArgs.Error -> {
                val title = context.getString(R.string.notification_dca_failed)
                val message = context.getString(
                    R.string.notification_dca_failed_text,
                    args.crypto,
                    args.errorMessage
                )
                title to message
            }

            is NotificationTemplateArgs.LowBalance -> {
                val title = context.getString(R.string.notification_low_balance_title, args.exchangeName)
                val daysText = if (args.remainingDays < 1) {
                    context.getString(R.string.notification_low_balance_less_1_day)
                } else {
                    context.getString(R.string.notification_low_balance_days, args.remainingDays.toInt())
                }
                val message = context.getString(R.string.notification_low_balance_text, daysText, args.fiat)
                title to message
            }

            is NotificationTemplateArgs.WithdrawalThreshold -> {
                val title = context.getString(R.string.notification_withdrawal_threshold_title)
                val message = context.getString(
                    R.string.notification_withdrawal_threshold_text,
                    args.amount,
                    args.crypto,
                    args.exchangeName
                )
                title to message
            }

            is NotificationTemplateArgs.TargetReached -> {
                val title = context.getString(R.string.notification_dca_failed)
                val message = context.getString(
                    R.string.notification_target_reached,
                    args.targetAmount,
                    args.crypto
                )
                title to message
            }

            is NotificationTemplateArgs.BelowMinimum -> {
                val title = context.getString(R.string.notification_dca_failed)
                val message = context.getString(
                    R.string.notification_dca_failed_text,
                    args.crypto,
                    context.getString(
                        R.string.notification_below_minimum,
                        args.purchaseAmount,
                        args.fiat,
                        args.minOrderSize
                    )
                )
                title to message
            }

            is NotificationTemplateArgs.MissedPurchases -> {
                val title = context.getString(R.string.notification_missed_purchases_title)
                val message = context.getString(
                    R.string.notification_missed_purchases_text,
                    args.missedCount,
                    args.crypto,
                    args.exchangeName
                )
                title to message
            }

            is NotificationTemplateArgs.NetworkRetry -> {
                val title = context.getString(R.string.notification_network_retry_title)
                val message = context.getString(
                    R.string.notification_network_retry_text,
                    args.crypto,
                    args.exchangeName
                )
                title to message
            }

            is NotificationTemplateArgs.MissingCredentials -> {
                val title = context.getString(R.string.notification_missing_credentials_title)
                val label = if (args.connectionName.isBlank()) args.exchangeName
                    else "${args.exchangeName} (${args.connectionName})"
                val message = context.getString(
                    R.string.notification_missing_credentials_text,
                    args.crypto,
                    label
                )
                title to message
            }
        }
    }

    private fun formatDelaySuffix(context: Context, scheduledAtEpochMs: Long?, executedAtEpochMs: Long?): String {
        if (scheduledAtEpochMs == null || executedAtEpochMs == null) return ""
        val delayMinutes = (executedAtEpochMs - scheduledAtEpochMs) / 60_000
        if (delayMinutes < 5) return ""
        val zone = ZoneId.systemDefault()
        val scheduledTime = Instant.ofEpochMilli(scheduledAtEpochMs).atZone(zone).format(timeFormatter)
        val executedTime = Instant.ofEpochMilli(executedAtEpochMs).atZone(zone).format(timeFormatter)
        return "\n" + context.getString(R.string.notification_purchase_delayed, scheduledTime, executedTime, delayMinutes)
    }
}
