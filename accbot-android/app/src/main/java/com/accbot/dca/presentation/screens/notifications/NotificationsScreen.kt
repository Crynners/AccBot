package com.accbot.dca.presentation.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accbot.dca.R
import com.accbot.dca.data.local.NotificationType
import com.accbot.dca.domain.model.AppNotification
import com.accbot.dca.presentation.components.EmptyState
import com.accbot.dca.presentation.ui.theme.Error
import com.accbot.dca.presentation.ui.theme.Warning
import com.accbot.dca.presentation.ui.theme.successColor
import com.accbot.dca.presentation.utils.DateFormatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val archivedNotifications by viewModel.archivedNotifications.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val archivedCount by viewModel.archivedCount.collectAsStateWithLifecycle()
    var showArchive by rememberSaveable { mutableStateOf(false) }
    var showClearArchiveDialog by rememberSaveable { mutableStateOf(false) }

    if (showClearArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showClearArchiveDialog = false },
            title = { Text(stringResource(R.string.notifications_clear_archive_dialog_title)) },
            text = { Text(stringResource(R.string.notifications_clear_archive_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearArchive()
                        showClearArchiveDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Error)
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearArchiveDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            if (showArchive) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.notifications_archive),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { showArchive = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }
                    },
                    actions = {
                        if (archivedNotifications.isNotEmpty()) {
                            TextButton(onClick = { showClearArchiveDialog = true }) {
                                Text(stringResource(R.string.notifications_clear_archive))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.notifications_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        if (notifications.isNotEmpty()) {
                            TextButton(onClick = { viewModel.archiveAll() }) {
                                Text(stringResource(R.string.notifications_dismiss_all))
                            }
                        }
                        IconButton(onClick = { showArchive = true }) {
                            BadgedBox(
                                badge = {
                                    if (archivedCount > 0) {
                                        Badge { Text("$archivedCount") }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Archive,
                                    contentDescription = stringResource(R.string.notifications_archive)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { paddingValues ->
        if (showArchive) {
            // Archive view
            if (archivedNotifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.Archive,
                        title = stringResource(R.string.notifications_archive_empty_title),
                        description = stringResource(R.string.notifications_archive_empty_description)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(archivedNotifications, key = { it.id }) { notification ->
                        NotificationItem(
                            notification = notification,
                            onClick = { },
                            modifier = Modifier.animateItem()
                        )
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        } else {
            // Active notifications view
            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.Notifications,
                        title = stringResource(R.string.notifications_empty_title),
                        description = stringResource(R.string.notifications_empty_description)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(notifications, key = { it.id }) { notification ->
                        NotificationItem(
                            notification = notification,
                            onClick = {
                                viewModel.archiveNotification(notification.id)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(
    notification: AppNotification,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val successCol = successColor()
    val containerColor = if (!notification.isRead) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            NotificationTypeIcon(type = notification.type)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (!notification.isRead) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(successCol)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = DateFormatters.transactionDateTime.format(notification.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NotificationTypeIcon(type: NotificationType) {
    val successCol = successColor()
    val (icon, color) = when (type) {
        NotificationType.PURCHASE -> Icons.Default.CheckCircle to successCol
        NotificationType.ERROR -> Icons.Default.Error to Error
        NotificationType.LOW_BALANCE -> Icons.Default.Warning to Warning
        NotificationType.WITHDRAWAL_THRESHOLD -> Icons.AutoMirrored.Filled.CallMade to Warning
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
    }
}
