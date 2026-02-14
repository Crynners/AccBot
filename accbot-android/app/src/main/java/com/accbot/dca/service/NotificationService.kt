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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
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
     * Show notification for successful purchase
     */
    fun showPurchaseNotification(
        crypto: String,
        cryptoAmount: BigDecimal,
        fiatAmount: BigDecimal,
        fiat: String
    ) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PURCHASE)
            .setContentTitle(context.getString(R.string.notification_purchase_title))
            .setContentText(context.getString(R.string.notification_purchase_text, cryptoAmount.toPlainString(), crypto, fiatAmount.toPlainString(), fiat))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_PURCHASE, notification)
    }

    /**
     * Show error notification
     */
    fun showErrorNotification(title: String, message: String) {
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

        notificationManager.notify(NOTIFICATION_ID_ERROR, notification)
    }

    /**
     * Show low balance warning notification
     */
    fun showLowBalanceNotification(exchange: String, fiat: String, remainingDays: Double) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val daysText = if (remainingDays < 1) context.getString(R.string.notification_low_balance_less_1_day) else context.getString(R.string.notification_low_balance_days, remainingDays.toInt())
        val notification = NotificationCompat.Builder(context, CHANNEL_LOW_BALANCE)
            .setContentTitle(context.getString(R.string.notification_low_balance_title, exchange))
            .setContentText(context.getString(R.string.notification_low_balance_text, daysText, fiat))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_LOW_BALANCE, notification)
    }

    companion object {
        const val CHANNEL_SERVICE = "accbot_service"
        const val CHANNEL_PURCHASE = "accbot_purchase"
        const val CHANNEL_ERROR = "accbot_error"
        const val CHANNEL_LOW_BALANCE = "accbot_low_balance"

        const val NOTIFICATION_ID_SERVICE = 1
        const val NOTIFICATION_ID_PURCHASE = 2
        const val NOTIFICATION_ID_ERROR = 3
        const val NOTIFICATION_ID_LOW_BALANCE = 4
    }
}
