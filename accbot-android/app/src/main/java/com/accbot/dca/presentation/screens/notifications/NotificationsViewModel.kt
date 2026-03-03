package com.accbot.dca.presentation.screens.notifications

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.NotificationDao
import com.accbot.dca.data.local.toDomain
import com.accbot.dca.domain.model.AppNotification
import com.accbot.dca.service.NotificationService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.accbot.dca.data.local.NotificationTemplateArgs
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationDao: NotificationDao,
    private val notificationService: NotificationService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _localeTrigger = MutableStateFlow(0L)

    val notifications: StateFlow<List<AppNotification>> = combine(
        notificationDao.getActiveNotifications(),
        _localeTrigger
    ) { entities, _ ->
        val localeContext = createLocaleAwareContext()
        entities.map { entity ->
            val (title, message) = NotificationRenderer.render(localeContext, entity)
            entity.toDomain().copy(title = title, message = message)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshForLocale() {
        _localeTrigger.value++
    }

    private fun createLocaleAwareContext(): Context {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (appLocales.isEmpty) return context
        val locale = appLocales[0] ?: return context
        val config = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(config)
    }

    val unreadCount: StateFlow<Int> = notificationDao.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun markAsRead(id: Long) {
        viewModelScope.launch {
            val sysId = notificationDao.getSystemNotificationId(id)
            notificationDao.markAsRead(id)
            if (sysId != null) {
                notificationService.cancelNotification(sysId)
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            notificationDao.markAllAsRead()
            notificationService.cancelAllNotifications()
        }
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch {
            val sysId = notificationDao.getSystemNotificationId(id)
            notificationDao.deleteNotification(id)
            if (sysId != null) {
                notificationService.cancelNotification(sysId)
            }
        }
    }

    fun deleteAllNotifications() {
        viewModelScope.launch {
            notificationDao.deleteAllNotifications()
            notificationService.cancelAllNotifications()
        }
    }

    fun createTestNotifications() {
        viewModelScope.launch {
            notificationService.showPurchaseNotification(
                crypto = "BTC",
                cryptoAmount = BigDecimal("0.00042"),
                fiatAmount = BigDecimal("25.00"),
                fiat = "EUR",
                price = BigDecimal("59523.81"),
                planId = 901
            )
            notificationService.showErrorNotification(
                planId = 902,
                templateArgs = NotificationTemplateArgs.Error(
                    crypto = "BTC",
                    errorMessage = "Insufficient balance on Binance"
                )
            )
            notificationService.showLowBalanceNotification(
                exchange = "Binance",
                fiat = "EUR",
                remainingDays = 3.0,
                planId = 903
            )
            notificationService.showWithdrawalThresholdNotification(
                crypto = "BTC",
                exchange = "Binance",
                amount = BigDecimal("0.01"),
                threshold = BigDecimal("0.01"),
                planId = 904
            )
            // Target reached
            notificationService.showErrorNotification(
                planId = 905,
                templateArgs = NotificationTemplateArgs.TargetReached(
                    targetAmount = "0.5",
                    crypto = "BTC"
                )
            )
            // Below minimum
            notificationService.showErrorNotification(
                planId = 906,
                templateArgs = NotificationTemplateArgs.BelowMinimum(
                    crypto = "BTC",
                    purchaseAmount = "5",
                    fiat = "EUR",
                    minOrderSize = "10"
                )
            )
        }
    }
}
