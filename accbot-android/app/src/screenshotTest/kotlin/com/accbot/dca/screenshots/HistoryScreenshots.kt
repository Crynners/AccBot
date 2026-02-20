package com.accbot.dca.screenshots

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.accbot.dca.R
import com.accbot.dca.presentation.screens.TransactionCard
import com.accbot.dca.presentation.ui.theme.AccBotTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryPreviewContent() {
    val transactions = SampleData.transactions

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.SwapVert, contentDescription = null)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.FilterList, contentDescription = null)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            items(transactions, key = { it.id }) { transaction ->
                TransactionCard(
                    transaction = transaction,
                    onClick = {}
                )
            }

            item {
                Text(
                    text = stringResource(R.string.history_transactions_count, transactions.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ── Phone 6.5" ──

@PreviewTest
@Preview(name = "History_Phone_EN", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun HistoryPhoneEn() {
    AccBotTheme(darkTheme = true) { HistoryPreviewContent() }
}

@PreviewTest
@Preview(name = "History_Phone_CS", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun HistoryPhoneCs() {
    AccBotTheme(darkTheme = true) { HistoryPreviewContent() }
}

// ── 7" Tablet ──

@PreviewTest
@Preview(name = "History_7inch_EN", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun HistoryTablet7En() {
    AccBotTheme(darkTheme = true) { HistoryPreviewContent() }
}

@PreviewTest
@Preview(name = "History_7inch_CS", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun HistoryTablet7Cs() {
    AccBotTheme(darkTheme = true) { HistoryPreviewContent() }
}

// ── 10" Tablet ──

@PreviewTest
@Preview(name = "History_10inch_EN", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun HistoryTablet10En() {
    AccBotTheme(darkTheme = true) { HistoryPreviewContent() }
}

@PreviewTest
@Preview(name = "History_10inch_CS", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun HistoryTablet10Cs() {
    AccBotTheme(darkTheme = true) { HistoryPreviewContent() }
}
