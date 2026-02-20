package com.accbot.dca.screenshots

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.screenshot.PreviewTest
import com.accbot.dca.R
import com.accbot.dca.presentation.screens.onboarding.FeatureCard
import com.accbot.dca.presentation.ui.theme.AccBotTheme
import com.accbot.dca.presentation.ui.theme.accentColor

/**
 * Static reconstruction of WelcomeScreen layout without LaunchedEffect animations.
 * In @Preview, LaunchedEffect doesn't run so Animatable stays at 0 (invisible cards).
 * This composable renders the same layout with all feature cards fully visible.
 */
@Composable
private fun WelcomePreviewContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        Text(
            text = stringResource(R.string.app_name),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.welcome_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FeatureCard(
                icon = Icons.Default.Loop,
                title = stringResource(R.string.welcome_auto_dca_title),
                description = stringResource(R.string.welcome_auto_dca_desc)
            )

            FeatureCard(
                icon = Icons.Default.Security,
                title = stringResource(R.string.welcome_self_custody_title),
                description = stringResource(R.string.welcome_self_custody_desc)
            )

            FeatureCard(
                icon = Icons.Default.Savings,
                title = stringResource(R.string.welcome_stack_sats_title),
                description = stringResource(R.string.welcome_stack_sats_desc)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

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
                text = stringResource(R.string.welcome_get_started),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ── Phone 6.5" ──

@PreviewTest
@Preview(name = "Welcome_Phone_EN", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun WelcomePhoneEn() {
    AccBotTheme(darkTheme = true) { WelcomePreviewContent() }
}

@PreviewTest
@Preview(name = "Welcome_Phone_CS", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun WelcomePhoneCs() {
    AccBotTheme(darkTheme = true) { WelcomePreviewContent() }
}

// ── 7" Tablet ──

@PreviewTest
@Preview(name = "Welcome_7inch_EN", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun WelcomeTablet7En() {
    AccBotTheme(darkTheme = true) { WelcomePreviewContent() }
}

@PreviewTest
@Preview(name = "Welcome_7inch_CS", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun WelcomeTablet7Cs() {
    AccBotTheme(darkTheme = true) { WelcomePreviewContent() }
}

// ── 10" Tablet ──

@PreviewTest
@Preview(name = "Welcome_10inch_EN", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun WelcomeTablet10En() {
    AccBotTheme(darkTheme = true) { WelcomePreviewContent() }
}

@PreviewTest
@Preview(name = "Welcome_10inch_CS", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun WelcomeTablet10Cs() {
    AccBotTheme(darkTheme = true) { WelcomePreviewContent() }
}
