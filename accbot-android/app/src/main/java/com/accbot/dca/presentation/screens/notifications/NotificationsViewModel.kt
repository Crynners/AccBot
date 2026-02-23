package com.accbot.dca.presentation.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.NotificationDao
import com.accbot.dca.data.local.toDomain
import com.accbot.dca.domain.model.AppNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationDao: NotificationDao
) : ViewModel() {

    val notifications: StateFlow<List<AppNotification>> = notificationDao.getActiveNotifications()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedNotifications: StateFlow<List<AppNotification>> = notificationDao.getArchivedNotifications()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> = notificationDao.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val archivedCount: StateFlow<Int> = notificationDao.getArchivedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun archiveNotification(id: Long) {
        viewModelScope.launch {
            notificationDao.archiveNotification(id)
        }
    }

    fun archiveAll() {
        viewModelScope.launch {
            notificationDao.archiveAllNotifications()
        }
    }

    fun clearArchive() {
        viewModelScope.launch {
            notificationDao.deleteArchivedNotifications()
        }
    }
}
