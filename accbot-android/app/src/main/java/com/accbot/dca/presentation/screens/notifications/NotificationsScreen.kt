package com.accbot.dca.presentation.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accbot.dca.R
import com.accbot.dca.data.local.NotificationType
import com.accbot.dca.domain.model.AppNotification
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.EmptyState
import com.accbot.dca.presentation.ui.theme.Error
import com.accbot.dca.presentation.ui.theme.LocalSandboxMode
import com.accbot.dca.presentation.ui.theme.Warning
import com.accbot.dca.presentation.ui.theme.successColor
import com.accbot.dca.presentation.utils.DateFormatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { viewModel.refreshForLocale() }

    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()

    // Auto-mark all as read after 2s on this screen
    LaunchedEffect(unreadCount) {
        if (unreadCount > 0) {
            kotlinx.coroutines.delay(2_000L)
            viewModel.markAllAsRead()
        }
    }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.notifications_delete_all)) },
            text = { Text(stringResource(R.string.settings_delete_notifications_dialog_text, notifications.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllNotifications()
                        showDeleteAllDialog = false
                    }
                ) {
                    Text(stringResource(R.string.common_delete), color = Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.notifications_title),
                actions = {
                    if (LocalSandboxMode.current) {
                        IconButton(onClick = { viewModel.createTestNotifications() }) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = stringResource(R.string.notifications_create_test)
                            )
                        }
                    }
                    if (unreadCount > 0) {
                        TextButton(onClick = { viewModel.markAllAsRead() }) {
                            Text(stringResource(R.string.notifications_mark_all_read))
                        }
                    } else if (notifications.isNotEmpty()) {
                        TextButton(onClick = { showDeleteAllDialog = true }) {
                            Text(stringResource(R.string.notifications_delete_all))
                        }
                    }
                }
            )
        },
    ) { paddingValues ->
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
                    SwipeToDismissNotification(
                        notification = notification,
                        onDismiss = { viewModel.deleteNotification(notification.id) },
                        onClick = { viewModel.markAsRead(notification.id) },
                        modifier = Modifier.animateItem()
                    )
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissNotification(
    notification: AppNotification,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState()
    var isFingerDown by remember { mutableStateOf(false) }

    // Only delete when finger is lifted AND state has settled at EndToStart
    LaunchedEffect(Unit) {
        snapshotFlow { isFingerDown to dismissState.currentValue }
            .collect { (fingerDown, value) ->
                if (!fingerDown && value == SwipeToDismissBoxValue.EndToStart) {
                    onDismiss()
                }
            }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    isFingerDown = event.changes.any { it.pressed }
                }
            }
        },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Error),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.padding(end = 20.dp)
                )
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        NotificationItem(
            notification = notification,
            onClick = onClick
        )
    }
}

@Composable
private fun NotificationItem(
    notification: AppNotification,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val successCol = successColor()
    val readStateDesc = stringResource(
        if (!notification.isRead) R.string.notification_state_unread
        else R.string.notification_state_read
    )
    val containerColor = if (!notification.isRead) {
        lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primaryContainer, 0.15f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { stateDescription = readStateDesc }
            .clickable(role = Role.Button, onClick = onClick),
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
        NotificationType.NETWORK_RETRY -> Icons.Default.WifiOff to Error
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
