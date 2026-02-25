package com.accbot.dca.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.accbot.dca.presentation.ui.theme.Success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeedPhraseGrid(
    seedWords: List<String>,
    onWordChange: (index: Int, word: String) -> Unit,
    onAllWordsChange: (List<String>) -> Unit,
    getSuggestions: (prefix: String) -> List<String>,
    isValidWord: (word: String) -> Boolean,
    modifier: Modifier = Modifier
) {
    val focusRequesters = remember { List(12) { FocusRequester() } }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in 0 until 3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0 until 4) {
                    val index = row * 4 + col
                    SeedWordInputField(
                        index = index,
                        word = seedWords[index],
                        onWordChange = { newValue ->
                            // Detect paste: if value contains spaces, split into all words
                            if (newValue.contains(" ") || newValue.contains("\n")) {
                                val tokens = newValue.trim().split("\\s+".toRegex())
                                    .filter { it.isNotBlank() }
                                    .map { it.lowercase() }
                                if (tokens.size > 1) {
                                    onAllWordsChange(tokens)
                                    // Move focus to last filled field or end
                                    val targetIndex = (tokens.size - 1).coerceAtMost(11)
                                    try { focusRequesters[targetIndex].requestFocus() } catch (_: Exception) {}
                                    return@SeedWordInputField
                                }
                            }
                            onWordChange(index, newValue.lowercase().trim())
                        },
                        onSuggestionSelected = { suggestion ->
                            onWordChange(index, suggestion)
                            // Auto-advance to next field
                            if (index < 11) {
                                try { focusRequesters[index + 1].requestFocus() } catch (_: Exception) {}
                            } else {
                                focusManager.clearFocus()
                            }
                        },
                        getSuggestions = getSuggestions,
                        isValidWord = isValidWord,
                        isLast = index == 11,
                        focusRequester = focusRequesters[index],
                        onNext = {
                            if (index < 11) {
                                try { focusRequesters[index + 1].requestFocus() } catch (_: Exception) {}
                            } else {
                                focusManager.clearFocus()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeedWordInputField(
    index: Int,
    word: String,
    onWordChange: (String) -> Unit,
    onSuggestionSelected: (String) -> Unit,
    getSuggestions: (String) -> List<String>,
    isValidWord: (String) -> Boolean,
    isLast: Boolean,
    focusRequester: FocusRequester,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(word) {
        if (word.length >= 2) getSuggestions(word) else emptyList()
    }

    val borderColor = when {
        word.isBlank() -> Color.Unspecified
        isValidWord(word) -> Success
        else -> MaterialTheme.colorScheme.error
    }

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = word,
            onValueChange = { newValue ->
                onWordChange(newValue)
                expanded = true
            },
            label = { Text("${index + 1}") },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (borderColor != Color.Unspecified) borderColor else MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (borderColor != Color.Unspecified) borderColor else MaterialTheme.colorScheme.outline
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = if (isLast) ImeAction.Done else ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { onNext() },
                onDone = { onNext() }
            ),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .focusRequester(focusRequester)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion, fontFamily = FontFamily.Monospace) },
                    onClick = {
                        expanded = false
                        onSuggestionSelected(suggestion)
                    }
                )
            }
        }
    }
}
