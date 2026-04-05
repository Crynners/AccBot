package com.accbot.dca.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.accbot.dca.MainActivity
import com.accbot.dca.R
import com.accbot.dca.data.local.NotificationDao
import com.accbot.dca.data.local.NotificationEntity
import com.accbot.dca.data.local.NotificationTemplateArgs
import com.accbot.dca.data.local.NotificationType
import com.accbot.dca.presentation.screens.notifications.NotificationRenderer
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.presentation.utils.NumberFormatters
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationDao: NotificationDao
) {
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // DCA Service channel - for foreground service
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                context.getString(R.string.notification_channel_service),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_service_desc)
                setShowBadge(false)
            }

            // Purchase notifications channel
            val purchaseChannel = NotificationChannel(
                CHANNEL_PURCHASE,
                context.getString(R.string.notification_channel_purchase),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_purchase_desc)
            }

            // Error notifications channel
            val errorChannel = NotificationChannel(
                CHANNEL_ERROR,
                context.getString(R.string.notification_channel_error),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_error_desc)
            }

            // Low balance notifications channel
            val lowBalanceChannel = NotificationChannel(
                CHANNEL_LOW_BALANCE,
                context.getString(R.string.notification_channel_low_balance),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_low_balance_desc)
            }

            notificationManager.createNotificationChannels(
                listOf(serviceChannel, purchaseChannel, errorChannel, lowBalanceChannel)
            )
        }
    }

    /**
     * Create notification for foreground service
     */
    fun createForegroundNotification(): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle(context.getString(R.string.notification_service_title))
            .setContentText(context.getString(R.string.notification_service_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Show notification for successful purchase.
     * Uses a unique notification ID per plan so multiple plan notifications are all visible.
     * @param pending If true, shows a "confirming" message instead of crypto amount (for PENDING orders)
     */
    fun showPurchaseNotification(
        crypto: String,
        cryptoAmount: BigDecimal,
        fiatAmount: BigDecimal,
        fiat: String,
        price: BigDecimal,
        planId: Long = 0,
        pending: Boolean = false,
        exchange: Exchange? = null,
        scheduledAt: java.time.Instant? = null,
        executedAt: java.time.Instant? = null
    ) {
        val scheduledMs = scheduledAt?.toEpochMilli()
        val executedMs = executedAt?.toEpochMilli()

        val args = if (pending) {
            NotificationTemplateArgs.PurchasePending(
                fiatAmount = fiatAmount.toPlainString(),
                fiat = fiat,
                crypto = crypto,
                price = price.toPlainString(),
                scheduledAtEpochMs = scheduledMs,
                executedAtEpochMs = executedMs
            )
        } else {
            NotificationTemplateArgs.Purchase(
                cryptoAmount = cryptoAmount.toPlainString(),
                crypto = crypto,
                fiatAmount = fiatAmount.toPlainString(),
                fiat = fiat,
                price = price.toPlainString(),
                scheduledAtEpochMs = scheduledMs,
                executedAtEpochMs = executedMs
            )
        }

        val (title, text) = NotificationRenderer.render(context, args)

        val sysNotifId = notificationIdForPlan(NOTIFICATION_ID_PURCHASE, planId)
        persistAndShow(
            sysNotifId = sysNotifId,
            channel = CHANNEL_PURCHASE,
            title = title,
            text = text,
            entity = NotificationEntity(
                type = NotificationType.PURCHASE,
                title = title,
                message = text,
                planId = planId.takeIf { it > 0 },
                crypto = crypto,
                exchange = exchange,
                systemNotificationId = sysNotifId,
                templateArgs = args.toJson()
            )
        )
    }

    /**
     * Show error notification.
     * Uses a unique notification ID per plan so multiple error notifications are all visible.
     */
    fun showErrorNotification(
        title: String? = null,
        message: String? = null,
        planId: Long = 0,
        exchange: Exchange? = null,
        crypto: String? = null,
        templateArgs: NotificationTemplateArgs? = null
    ) {
        val (t, m) = if (templateArgs != null) {
            NotificationRenderer.render(context, templateArgs)
        } else {
            (title ?: "") to (message ?: "")
        }
        val sysNotifId = notificationIdForPlan(NOTIFICATION_ID_ERROR, planId)
        persistAndShow(
            sysNotifId = sysNotifId,
            channel = CHANNEL_ERROR,
            title = t,
            text = m,
            entity = NotificationEntity(
                type = NotificationType.ERROR,
                title = t,
                message = m,
                planId = planId.takeIf { it > 0 },
                crypto = crypto,
                exchange = exchange,
                systemNotificationId = sysNotifId,
                templateArgs = templateArgs?.toJson()
            )
        )
    }

    /**
     * Show low balance warning notification.
     * Uses a unique notification ID per plan so multiple warnings are all visible.
     */
    fun showLowBalanceNotification(exchange: String, fiat: String, remainingDays: Double, planId: Long = 0) {
        val title = context.getString(R.string.notification_low_balance_title, exchange)
        val daysText = if (remainingDays < 1) context.getString(R.string.notification_low_balance_less_1_day) else context.getString(R.string.notification_low_balance_days, remainingDays.toInt())
        val text = context.getString(R.string.notification_low_balance_text, daysText, fiat)
        val args = NotificationTemplateArgs.LowBalance(
            exchangeName = exchange,
            fiat = fiat,
            remainingDays = remainingDays
        )
        val sysNotifId = notificationIdForPlan(NOTIFICATION_ID_LOW_BALANCE, planId)
        persistAndShow(
            sysNotifId = sysNotifId,
            channel = CHANNEL_LOW_BALANCE,
            title = title,
            text = text,
            entity = NotificationEntity(
                type = NotificationType.LOW_BALANCE,
                title = title,
                message = text,
                planId = planId.takeIf { it > 0 },
                systemNotificationId = sysNotifId,
                templateArgs = args.toJson()
            )
        )
    }

    /**
     * Show withdrawal threshold notification.
     */
    fun showWithdrawalThresholdNotification(
        crypto: String,
        exchange: String,
        amount: BigDecimal,
        threshold: BigDecimal,
        planId: Long
    ) {
        val title = context.getString(R.string.notification_withdrawal_threshold_title)
        val text = context.getString(R.string.notification_withdrawal_threshold_text, amount.toPlainString(), crypto, exchange)
        val args = NotificationTemplateArgs.WithdrawalThreshold(
            amount = amount.toPlainString(),
            crypto = crypto,
            exchangeName = exchange
        )
        val sysNotifId = notificationIdForPlan(NOTIFICATION_ID_WITHDRAWAL_THRESHOLD, planId)
        persistAndShow(
            sysNotifId = sysNotifId,
            channel = CHANNEL_LOW_BALANCE,
            title = title,
            text = text,
            entity = NotificationEntity(
                type = NotificationType.WITHDRAWAL_THRESHOLD,
                title = title,
                message = text,
                planId = planId.takeIf { it > 0 },
                crypto = crypto,
                systemNotificationId = sysNotifId,
                templateArgs = args.toJson()
            )
        )
    }

    /**
     * Show notification for network retry (offline purchase failure).
     */
    fun showNetworkRetryNotification(
        crypto: String,
        exchangeName: String,
        errorMessage: String,
        nextRetryAt: java.time.Instant,
        attemptCount: Int,
        planId: Long,
        exchange: Exchange? = null
    ) {
        val args = NotificationTemplateArgs.NetworkRetry(
            crypto = crypto,
            exchangeName = exchangeName,
            errorMessage = errorMessage,
            nextRetryAtEpochMs = nextRetryAt.toEpochMilli(),
            attemptCount = attemptCount,
            planId = planId
        )
        val (title, text) = NotificationRenderer.render(context, args)
        val sysNotifId = notificationIdForPlan(NOTIFICATION_ID_NETWORK_RETRY, planId)
        persistAndShow(
            sysNotifId = sysNotifId,
            channel = CHANNEL_ERROR,
            title = title,
            text = text,
            entity = NotificationEntity(
                type = NotificationType.NETWORK_RETRY,
                title = title,
                message = text,
                planId = planId.takeIf { it > 0 },
                crypto = crypto,
                exchange = exchange,
                systemNotificationId = sysNotifId,
                templateArgs = args.toJson()
            )
        )
    }

    /**
     * Cancel a specific system notification by its ID.
     */
    fun cancelNotification(systemNotificationId: Int) {
        notificationManager.cancel(systemNotificationId)
    }

    /**
     * Cancel all system notifications except the foreground service notification.
     * Note: cancelAll() does not cancel foreground service notifications.
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }

    /**
     * Persist notification to DB first, then show system notification with a PendingIntent
     * that carries the DB row ID so tapping it can mark the notification as read.
     */
    private fun persistAndShow(
        sysNotifId: Int,
        channel: String,
        title: String,
        text: String,
        entity: NotificationEntity
    ) {
        persistScope.launch {
            try {
                val dbId = notificationDao.insert(entity)

                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra(EXTRA_NOTIFICATION_ID, dbId)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, sysNotifId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, channel)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(sysNotifId, notification)
            } catch (_: Exception) {
                // Best-effort — don't crash if DB write fails
            }
        }
    }

    companion object {
        const val CHANNEL_SERVICE = "accbot_service"
        const val CHANNEL_PURCHASE = "accbot_purchase"
        const val CHANNEL_ERROR = "accbot_error"
        const val CHANNEL_LOW_BALANCE = "accbot_low_balance"

        const val NOTIFICATION_ID_SERVICE = 1
        private const val NOTIFICATION_ID_PURCHASE = 10_000
        private const val NOTIFICATION_ID_ERROR = 20_000
        private const val NOTIFICATION_ID_LOW_BALANCE = 30_000
        private const val NOTIFICATION_ID_WITHDRAWAL_THRESHOLD = 40_000
        private const val NOTIFICATION_ID_NETWORK_RETRY = 50_000

        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        /**
         * Generate a unique notification ID per plan to prevent overwriting.
         * Each category (purchase/error/low_balance) gets a range of 10000 IDs.
         */
        private fun notificationIdForPlan(baseId: Int, planId: Long): Int {
            return if (planId > 0) baseId + ((planId % 9999) + 1).toInt() else baseId
        }
    }
}
