package com.accbot.dca.screenshots

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.screenshot.PreviewTest
import com.accbot.dca.R
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.presentation.components.CredentialsInputCard
import com.accbot.dca.presentation.screens.onboarding.ExchangeGridItem
import com.accbot.dca.presentation.ui.theme.AccBotTheme
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExchangeSetupPreviewContent() {
    val exchanges = Exchange.entries.toList()
    val selected = Exchange.COINMATE

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exchange_setup_title)) },
                actions = {
                    TextButton(onClick = {}) {
                        Text(stringResource(R.string.common_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress indicator
            LinearProgressIndicator(
                progress = { 0.5f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accentColor()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.exchange_setup_choose),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.exchange_setup_choose_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Exchange grid
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                exchanges.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { exchange ->
                            ExchangeGridItem(
                                exchange = exchange,
                                isSelected = exchange == selected,
                                onClick = {},
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Credentials input
            Spacer(modifier = Modifier.height(24.dp))

            CredentialsInputCard(
                exchange = selected,
                clientId = "12345",
                apiKey = "aBcDeFgHiJkLmNoPqRsT",
                apiSecret = "xYzAbCdEfGhIjKlMnOpQ",
                passphrase = "",
                onClientIdChange = {},
                onApiKeyChange = {},
                onApiSecretChange = {},
                onPassphraseChange = {}
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor()
                )
            ) {
                Text(
                    text = stringResource(R.string.exchange_setup_connect),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Phone 6.5" ──

@PreviewTest
@Preview(name = "ExchangeSetup_Phone_EN", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ExchangeSetupPhoneEn() {
    AccBotTheme(darkTheme = true) { ExchangeSetupPreviewContent() }
}

@PreviewTest
@Preview(name = "ExchangeSetup_Phone_CS", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun ExchangeSetupPhoneCs() {
    AccBotTheme(darkTheme = true) { ExchangeSetupPreviewContent() }
}

// ── 7" Tablet ──

@PreviewTest
@Preview(name = "ExchangeSetup_7inch_EN", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ExchangeSetupTablet7En() {
    AccBotTheme(darkTheme = true) { ExchangeSetupPreviewContent() }
}

@PreviewTest
@Preview(name = "ExchangeSetup_7inch_CS", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun ExchangeSetupTablet7Cs() {
    AccBotTheme(darkTheme = true) { ExchangeSetupPreviewContent() }
}

// ── 10" Tablet ──

@PreviewTest
@Preview(name = "ExchangeSetup_10inch_EN", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ExchangeSetupTablet10En() {
    AccBotTheme(darkTheme = true) { ExchangeSetupPreviewContent() }
}

@PreviewTest
@Preview(name = "ExchangeSetup_10inch_CS", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun ExchangeSetupTablet10Cs() {
    AccBotTheme(darkTheme = true) { ExchangeSetupPreviewContent() }
}
