package com.accbot.dca.screenshots

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.accbot.dca.R
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.CredentialsInputCard
import com.accbot.dca.presentation.ui.theme.AccBotTheme
import com.accbot.dca.presentation.ui.theme.accentColor

/**
 * Shows the credentials step (step 3 of the standalone add-exchange wizard).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExchangePreviewContent() {
    val exchange = Exchange.KRAKEN

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            Column {
                AccBotTopAppBar(
                    title = stringResource(R.string.add_exchange_credentials),
                    onNavigateBack = {}
                )
                // Step progress indicator (step 3 of 4 = 75%)
                LinearProgressIndicator(
                    progress = { 0.75f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = accentColor()
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.add_exchange_enter_credentials, exchange.displayName),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            CredentialsInputCard(
                exchange = exchange,
                clientId = "",
                apiKey = "KrAkEnApIkEyExAmPlE1234",
                apiSecret = "sEcReTkEyExAmPlE5678AbCd",
                passphrase = "",
                onClientIdChange = {},
                onApiKeyChange = {},
                onApiSecretChange = {},
                onPassphraseChange = {}
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor())
            ) {
                Text(
                    text = stringResource(R.string.add_exchange_connect),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Phone 6.5" ──

@PreviewTest
@Preview(name = "AddExchange_Phone_EN", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun AddExchangePhoneEn() {
    AccBotTheme(darkTheme = true) { AddExchangePreviewContent() }
}

@PreviewTest
@Preview(name = "AddExchange_Phone_CS", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun AddExchangePhoneCs() {
    AccBotTheme(darkTheme = true) { AddExchangePreviewContent() }
}

// ── 7" Tablet ──

@PreviewTest
@Preview(name = "AddExchange_7inch_EN", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun AddExchangeTablet7En() {
    AccBotTheme(darkTheme = true) { AddExchangePreviewContent() }
}

@PreviewTest
@Preview(name = "AddExchange_7inch_CS", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun AddExchangeTablet7Cs() {
    AccBotTheme(darkTheme = true) { AddExchangePreviewContent() }
}

// ── 10" Tablet ──

@PreviewTest
@Preview(name = "AddExchange_10inch_EN", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun AddExchangeTablet10En() {
    AccBotTheme(darkTheme = true) { AddExchangePreviewContent() }
}

@PreviewTest
@Preview(name = "AddExchange_10inch_CS", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun AddExchangeTablet10Cs() {
    AccBotTheme(darkTheme = true) { AddExchangePreviewContent() }
}
