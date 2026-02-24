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
import com.accbot.dca.data.local.NotificationType
import com.accbot.dca.domain.model.Exchange
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
        exchange: Exchange? = null
    ) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.notification_purchase_title)
        val priceFormatted = price.stripTrailingZeros().toPlainString()
        val text = if (pending) {
            context.getString(R.string.notification_purchase_pending_text, fiatAmount.toPlainString(), fiat, crypto, priceFormatted)
        } else {
            context.getString(R.string.notification_purchase_text, cryptoAmount.toPlainString(), crypto, fiatAmount.toPlainString(), fiat, priceFormatted)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_PURCHASE)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationIdForPlan(NOTIFICATION_ID_PURCHASE, planId), notification)

        persistNotification(NotificationType.PURCHASE, title, text, planId.takeIf { it > 0 }, crypto, exchange)
    }

    /**
     * Show error notification.
     * Uses a unique notification ID per plan so multiple error notifications are all visible.
     */
    fun showErrorNotification(title: String, message: String, planId: Long = 0, exchange: Exchange? = null, crypto: String? = null) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ERROR)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationIdForPlan(NOTIFICATION_ID_ERROR, planId), notification)

        persistNotification(NotificationType.ERROR, title, message, planId.takeIf { it > 0 }, crypto, exchange)
    }

    /**
     * Show low balance warning notification.
     * Uses a unique notification ID per plan so multiple warnings are all visible.
     */
    fun showLowBalanceNotification(exchange: String, fiat: String, remainingDays: Double, planId: Long = 0) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.notification_low_balance_title, exchange)
        val daysText = if (remainingDays < 1) context.getString(R.string.notification_low_balance_less_1_day) else context.getString(R.string.notification_low_balance_days, remainingDays.toInt())
        val text = context.getString(R.string.notification_low_balance_text, daysText, fiat)
        val notification = NotificationCompat.Builder(context, CHANNEL_LOW_BALANCE)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationIdForPlan(NOTIFICATION_ID_LOW_BALANCE, planId), notification)

        persistNotification(NotificationType.LOW_BALANCE, title, text, planId.takeIf { it > 0 })
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
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.notification_withdrawal_threshold_title)
        val text = context.getString(R.string.notification_withdrawal_threshold_text, amount.toPlainString(), crypto, exchange)

        val notification = NotificationCompat.Builder(context, CHANNEL_LOW_BALANCE)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationIdForPlan(NOTIFICATION_ID_WITHDRAWAL_THRESHOLD, planId), notification)

        persistNotification(NotificationType.WITHDRAWAL_THRESHOLD, title, text, planId.takeIf { it > 0 }, crypto)
    }

    private fun persistNotification(
        type: NotificationType,
        title: String,
        message: String,
        planId: Long? = null,
        crypto: String? = null,
        exchange: Exchange? = null
    ) {
        persistScope.launch {
            try {
                notificationDao.insert(
                    NotificationEntity(
                        type = type,
                        title = title,
                        message = message,
                        planId = planId,
                        crypto = crypto,
                        exchange = exchange
                    )
                )
            } catch (_: Exception) {
                // Best-effort persistence — don't crash if DB write fails
            }
        }
    }

    companion object {
        const val CHANNEL_SERVICE = "accbot_service"
        const val CHANNEL_PURCHASE = "accbot_purchase"
        const val CHANNEL_ERROR = "accbot_error"
        const val CHANNEL_LOW_BALANCE = "accbot_low_balance"

        const val NOTIFICATION_ID_SERVICE = 1
        private const val NOTIFICATION_ID_PURCHASE = 100
        private const val NOTIFICATION_ID_ERROR = 200
        private const val NOTIFICATION_ID_LOW_BALANCE = 300
        private const val NOTIFICATION_ID_WITHDRAWAL_THRESHOLD = 400

        /**
         * Generate a unique notification ID per plan to prevent overwriting.
         * Each category (purchase/error/low_balance) gets a range of 100 IDs.
         */
        private fun notificationIdForPlan(baseId: Int, planId: Long): Int {
            return if (planId > 0) baseId + (planId % 99).toInt() + 1 else baseId
        }
    }
}
