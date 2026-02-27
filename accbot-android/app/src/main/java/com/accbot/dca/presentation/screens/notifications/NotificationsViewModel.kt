package com.accbot.dca.presentation.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.NotificationDao
import com.accbot.dca.data.local.toDomain
import com.accbot.dca.domain.model.AppNotification
import com.accbot.dca.service.NotificationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationDao: NotificationDao,
    private val notificationService: NotificationService
) : ViewModel() {

    val notifications: StateFlow<List<AppNotification>> = notificationDao.getActiveNotifications()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                title = "DCA Failed",
                message = "Insufficient balance on Binance for BTC/EUR",
                planId = 902
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
        }
    }
}
