package com.accbot.dca.presentation.screens.backup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun BackupStepIndicator(steps: List<String>, currentStep: Int, onStepClick: ((Int) -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        steps.forEachIndexed { index, label ->
            val isClickable = onStepClick != null && index < currentStep
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isClickable) Modifier.clickable { onStepClick!!(index) }
                        else Modifier
                    )
            ) {
                val isActive = index <= currentStep
                val color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isActive) color else color.copy(alpha = 0.2f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .height(4.dp)
                ) {}
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
