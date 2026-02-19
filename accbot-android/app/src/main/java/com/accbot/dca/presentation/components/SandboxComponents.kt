package com.accbot.dca.presentation.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.accbot.dca.R
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeInstructions
import com.accbot.dca.presentation.ui.theme.Warning

/**
 * Displays a warning indicator when sandbox mode is active.
 * Reusable across different screens.
 */
@Composable
fun SandboxModeIndicator(
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.15f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Science,
                contentDescription = null,
                tint = Warning,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.sandbox_mode_indicator),
                color = Warning,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Card with testnet credentials instructions and link to exchange sandbox.
 * Shows step-by-step guide for obtaining testnet API credentials.
 */
@Composable
fun SandboxCredentialsInfoCard(
    exchange: Exchange,
    instructions: ExchangeInstructions,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.15f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Science,
                    contentDescription = null,
                    tint = Warning
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.sandbox_testnet_required),
                    fontWeight = FontWeight.SemiBold,
                    color = Warning
                )
            }

            Text(
                stringResource(R.string.sandbox_testnet_credentials_desc, exchange.displayName),
                style = MaterialTheme.typography.bodyMedium
            )

            instructions.steps.forEachIndexed { index, stepResId ->
                Row {
                    Text(
                        "${index + 1}.",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(stringResource(stepResId), style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(instructions.url))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Warning)
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_exchange_open_testnet, exchange.displayName))
            }
        }
    }
}
