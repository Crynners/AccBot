package com.accbot.dca.presentation.screens.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.presentation.components.NextStepItem
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor
import kotlinx.coroutines.delay

@Composable
fun CompletionScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Success animation
    val scale = remember { Animatable(0f) }
    val checkScale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        delay(200)
        checkScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Success icon with animation
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale.value)
                .clip(CircleShape)
                .background(successColor().copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = successColor(),
                modifier = Modifier
                    .size(60.dp)
                    .scale(checkScale.value)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.completion_title),
            style = MaterialTheme.typography.headlineMedium,
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

                if (uiState.planCreated) {
                    NextStepItem(
                        icon = Icons.Default.CheckCircle,
                        title = stringResource(R.string.completion_plan_active_title),
                        description = stringResource(R.string.completion_plan_active_desc),
                        size = 36.dp,
                        iconSize = 18.dp,
                        cornerRadius = 8.dp,
                        spacing = 12.dp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    NextStepItem(
                        icon = Icons.Default.Add,
                        title = stringResource(R.string.completion_add_more_title),
                        description = stringResource(R.string.completion_add_more_desc),
                        size = 36.dp,
                        iconSize = 18.dp,
                        cornerRadius = 8.dp,
                        spacing = 12.dp
                    )
                } else {
                    NextStepItem(
                        icon = Icons.Default.PlayArrow,
                        title = stringResource(R.string.completion_start_service_title),
                        description = stringResource(R.string.completion_start_service_desc),
                        size = 36.dp,
                        iconSize = 18.dp,
                        cornerRadius = 8.dp,
                        spacing = 12.dp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    NextStepItem(
                        icon = Icons.Default.Tune,
                        title = stringResource(R.string.completion_fine_tune_title),
                        description = stringResource(R.string.completion_fine_tune_desc),
                        size = 36.dp,
                        iconSize = 18.dp,
                        cornerRadius = 8.dp,
                        spacing = 12.dp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Finish button
        Button(
            onClick = {
                viewModel.completeOnboarding()
                onFinish()
            },
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

