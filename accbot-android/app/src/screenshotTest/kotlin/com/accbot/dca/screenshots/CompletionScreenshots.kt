package com.accbot.dca.screenshots

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.accbot.dca.presentation.screens.onboarding.NextStepItem
import com.accbot.dca.presentation.ui.theme.AccBotTheme
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor

/**
 * Static reconstruction of CompletionScreen without LaunchedEffect animations.
 * In @Preview, Animatable stays at 0 (invisible circle/check).
 * This composable renders at full scale so both are visible.
 */
@Composable
private fun CompletionPreviewContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Success icon (full scale, no animation)
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(successColor().copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = successColor(),
                modifier = Modifier.size(60.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.completion_title),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.completion_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // What's next section
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.completion_whats_next),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                NextStepItem(
                    icon = Icons.Default.PlayArrow,
                    title = stringResource(R.string.completion_start_service_title),
                    description = stringResource(R.string.completion_start_service_desc)
                )

                Spacer(modifier = Modifier.height(12.dp))

                NextStepItem(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.completion_fine_tune_title),
                    description = stringResource(R.string.completion_fine_tune_desc)
                )

                Spacer(modifier = Modifier.height(12.dp))

                NextStepItem(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.completion_stay_informed_title),
                    description = stringResource(R.string.completion_stay_informed_desc)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tips card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = accentColor().copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = accentColor(),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.completion_pro_tip),
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
                text = stringResource(R.string.completion_start_stacking),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ── Phone 6.5" ──

@PreviewTest
@Preview(name = "Completion_Phone_EN", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun CompletionPhoneEn() {
    AccBotTheme(darkTheme = true) { CompletionPreviewContent() }
}

@PreviewTest
@Preview(name = "Completion_Phone_CS", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun CompletionPhoneCs() {
    AccBotTheme(darkTheme = true) { CompletionPreviewContent() }
}

// ── 7" Tablet ──

@PreviewTest
@Preview(name = "Completion_7inch_EN", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun CompletionTablet7En() {
    AccBotTheme(darkTheme = true) { CompletionPreviewContent() }
}

@PreviewTest
@Preview(name = "Completion_7inch_CS", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun CompletionTablet7Cs() {
    AccBotTheme(darkTheme = true) { CompletionPreviewContent() }
}

// ── 10" Tablet ──

@PreviewTest
@Preview(name = "Completion_10inch_EN", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun CompletionTablet10En() {
    AccBotTheme(darkTheme = true) { CompletionPreviewContent() }
}

@PreviewTest
@Preview(name = "Completion_10inch_CS", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun CompletionTablet10Cs() {
    AccBotTheme(darkTheme = true) { CompletionPreviewContent() }
}
