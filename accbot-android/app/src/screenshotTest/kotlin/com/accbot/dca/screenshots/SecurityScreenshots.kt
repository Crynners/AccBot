package com.accbot.dca.screenshots

import android.content.res.Configuration
import androidx.compose.foundation.background
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
import com.accbot.dca.presentation.screens.onboarding.SecurityFeatureRow
import com.accbot.dca.presentation.ui.theme.AccBotTheme
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor

@Composable
private fun SecurityPreviewContent() {
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
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
                progress = { 0.25f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accentColor()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Security icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(successColor().copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = successColor(),
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.security_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.security_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Security features
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SecurityFeatureRow(
                    icon = Icons.Default.PhoneAndroid,
                    title = stringResource(R.string.security_local_title),
                    description = stringResource(R.string.security_local_desc)
                )
                SecurityFeatureRow(
                    icon = Icons.Default.CloudOff,
                    title = stringResource(R.string.security_no_cloud_title),
                    description = stringResource(R.string.security_no_cloud_desc)
                )
                SecurityFeatureRow(
                    icon = Icons.Default.Key,
                    title = stringResource(R.string.security_keystore_title),
                    description = stringResource(R.string.security_keystore_desc)
                )
                SecurityFeatureRow(
                    icon = Icons.Default.Https,
                    title = stringResource(R.string.security_direct_title),
                    description = stringResource(R.string.security_direct_desc)
                )
                SecurityFeatureRow(
                    icon = Icons.Default.Fingerprint,
                    title = stringResource(R.string.biometric_onboarding_title),
                    description = stringResource(R.string.biometric_onboarding_desc)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Biometric enable button (show as not yet enabled)
            OutlinedButton(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = successColor()
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${stringResource(R.string.biometric_onboarding_enable)} ${stringResource(R.string.biometric_lock_recommend)}",
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Info card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = accentColor().copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = accentColor(),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.security_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

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
                    text = stringResource(R.string.security_i_understand),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Phone 6.5" ──

@PreviewTest
@Preview(name = "Security_Phone_EN", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun SecurityPhoneEn() {
    AccBotTheme(darkTheme = true) { SecurityPreviewContent() }
}

@PreviewTest
@Preview(name = "Security_Phone_CS", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun SecurityPhoneCs() {
    AccBotTheme(darkTheme = true) { SecurityPreviewContent() }
}

// ── 7" Tablet ──

@PreviewTest
@Preview(name = "Security_7inch_EN", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun SecurityTablet7En() {
    AccBotTheme(darkTheme = true) { SecurityPreviewContent() }
}

@PreviewTest
@Preview(name = "Security_7inch_CS", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun SecurityTablet7Cs() {
    AccBotTheme(darkTheme = true) { SecurityPreviewContent() }
}

// ── 10" Tablet ──

@PreviewTest
@Preview(name = "Security_10inch_EN", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun SecurityTablet10En() {
    AccBotTheme(darkTheme = true) { SecurityPreviewContent() }
}

@PreviewTest
@Preview(name = "Security_10inch_CS", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun SecurityTablet10Cs() {
    AccBotTheme(darkTheme = true) { SecurityPreviewContent() }
}
