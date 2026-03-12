package com.accbot.dca.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.accbot.dca.R
import com.accbot.dca.domain.usecase.ApiImportResultState

@Composable
fun ApiImportResultDialog(
    result: ApiImportResultState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (result) {
                    is ApiImportResultState.Success -> stringResource(R.string.import_api_success_title)
                    is ApiImportResultState.Error -> stringResource(R.string.import_api_error_title)
                }
            )
        },
        text = {
            Text(
                when (result) {
                    is ApiImportResultState.Success -> {
                        if (result.imported == 0) {
                            stringResource(R.string.import_api_no_new)
                        } else {
                            stringResource(R.string.import_api_success_message, result.imported, result.skipped)
                        }
                    }
                    is ApiImportResultState.Error -> result.message
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_done))
            }
        }
    )
}
